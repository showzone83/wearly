package app.wearly.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.application.port.out.RefreshTokenPort;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.RefreshToken;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 가입·로그인·회전·정지 회원 처리 (가짜 포트, Spring 없이) */
class AuthServiceTest {

  static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  final FakeUsers users = new FakeUsers();
  final Map<Long, LocalCredential> credentialRows = new LinkedHashMap<>();
  final Map<Long, RefreshToken> tokenRows = new LinkedHashMap<>();
  final List<String> checkedHashes = new ArrayList<>();
  final List<Long> loginMarks = new ArrayList<>();

  /** 확인과 저장 사이에 다른 요청이 같은 이메일로 먼저 가입한 상황 */
  boolean concurrentSignup;

  final CredentialPort credentials =
      new CredentialPort() {
        @Override
        public Optional<LocalCredential> findByEmail(String email) {
          return credentialRows.values().stream().filter(c -> c.email().equals(email)).findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
          return findByEmail(email).isPresent();
        }

        @Override
        public Optional<LocalCredential> findByUserId(long userId) {
          return credentialRows.values().stream().filter(c -> c.userId() == userId).findFirst();
        }

        @Override
        public void create(LocalCredential c) {
          if (concurrentSignup) {
            throw new EmailTakenException(c.email());
          }
          long id = credentialRows.size() + 1L;
          credentialRows.put(id, new LocalCredential(id, c.userId(), c.email(), c.passwordHash()));
        }

        @Override
        public void markLogin(long credentialId, Instant at) {
          loginMarks.add(credentialId);
        }
      };

  final RefreshTokenPort tokens =
      new RefreshTokenPort() {
        @Override
        public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
          return tokenRows.values().stream()
              .filter(t -> t.tokenHash().equals(tokenHash))
              .findFirst();
        }

        @Override
        public RefreshToken save(RefreshToken t) {
          long id = t.id() == null ? tokenRows.size() + 1L : t.id();
          RefreshToken saved =
              new RefreshToken(
                  id,
                  t.userId(),
                  t.tokenHash(),
                  t.familyId(),
                  t.expiresAt(),
                  t.revokedAt(),
                  t.replacedById());
          tokenRows.put(id, saved);
          return saved;
        }

        @Override
        public void revokeFamily(UUID familyId, Instant at) {
          tokenRows.values().stream()
              .filter(t -> t.familyId().equals(familyId) && !t.isRevoked())
              .forEach(t -> t.revoke(at));
        }

        @Override
        public void revokeAllForUser(long userId, Instant at) {
          tokenRows.values().stream()
              .filter(t -> t.userId() == userId && !t.isRevoked())
              .forEach(t -> t.revoke(at));
        }
      };

  final PasswordHasher hasher =
      new PasswordHasher() {
        @Override
        public String encode(String raw) {
          return "hash:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
          checkedHashes.add(hash);
          return hash.equals("hash:" + raw);
        }
      };

  final AccessTokenIssuer issuer =
      new AccessTokenIssuer() {
        @Override
        public String issue(long userId, String role) {
          return "access:" + userId + ":" + role;
        }

        @Override
        public long accessTtlSeconds() {
          return 900;
        }
      };

  final AuthService service =
      new AuthService(
          users,
          credentials,
          new RefreshTokenService(tokens, new TokenPolicy(Duration.ofDays(14)), clock),
          issuer,
          hasher,
          clock);

  @Test
  void 가입하면_이메일을_정규화해_저장하고_토큰을_준다() {
    AuthResultDto r = service.signup(new SignupDto(" Kim@Wearly.Test ", "pass1234", " 김 "));

    assertThat(r.user().email()).isEqualTo("kim@wearly.test");
    assertThat(r.user().name()).isEqualTo("김");
    assertThat(r.user().role()).isEqualTo(UserAccountDto.ROLE_USER);
    assertThat(r.accessToken()).isEqualTo("access:" + r.user().id() + ":USER");
    assertThat(r.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(14).toSeconds());
    assertThat(credentialRows.values())
        .singleElement()
        .satisfies(c -> assertThat(c.passwordHash()).isEqualTo("hash:pass1234"));
    assertThat(tokenRows).hasSize(1);

    assertThatThrownBy(() -> service.signup(new SignupDto("KIM@wearly.test", "x1234567", "다른")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(users.rows).hasSize(1);
  }

  @Test
  void 동시_가입으로_저장이_막히면_EMAIL_TAKEN() {
    concurrentSignup = true;

    assertThatThrownBy(() -> service.signup(new SignupDto("race@wearly.test", "pass1234", "경쟁")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(tokenRows).isEmpty();
  }

  @Test
  void 없는_이메일도_비밀번호_비교를_한_번_하고_같은_오류를_낸다() {
    service.signup(new SignupDto("a@wearly.test", "pass1234", "에이"));
    checkedHashes.clear();

    assertThatThrownBy(() -> service.login(new LoginDto("none@wearly.test", "pass1234")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("a@wearly.test", "wrong")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThat(checkedHashes).containsExactly("hash:wearly-dummy-password-1", "hash:pass1234");
    assertThat(loginMarks).isEmpty();
  }

  @Test
  void 정지_회원은_비밀번호가_맞을_때만_정지를_알린다() {
    AuthResultDto r = service.signup(new SignupDto("s@wearly.test", "pass1234", "정지"));
    users.changeAccess(r.user().id(), UserAccountDto.SUSPENDED, UserAccountDto.ROLE_USER);

    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "wrong")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "pass1234")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_SUSPENDED));
    // 정지 회원의 Refresh 토큰도 받지 않는다
    assertThatThrownBy(() -> service.refresh(r.refreshToken()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(service.activeUserRole(r.user().id())).isEmpty();
  }

  @Test
  void 회전한_토큰을_다시_쓰면_family를_모두_폐기한다() {
    AuthResultDto first = service.login(signupAndLogin());
    AuthResultDto second = service.refresh(first.refreshToken());
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

    assertThatThrownBy(() -> service.refresh(first.refreshToken()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_REUSED));
    // 재사용을 감지하면 같은 family의 최신 토큰도 못 쓴다
    assertThatThrownBy(() -> service.refresh(second.refreshToken()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
  }

  @Test
  void 재인증은_빈_비밀번호도_더미_비교를_한다() {
    AuthResultDto r = service.signup(new SignupDto("r@wearly.test", "pass1234", "재인증"));
    checkedHashes.clear();

    assertThat(service.passwordMatches(r.user().id(), "")).isFalse();
    assertThat(service.passwordMatches(999, "pass1234")).isFalse();
    assertThat(service.passwordMatches(r.user().id(), "pass1234")).isTrue();
    assertThat(checkedHashes)
        .containsExactly(
            "hash:wearly-dummy-password-1", "hash:wearly-dummy-password-1", "hash:pass1234");
  }

  private LoginDto signupAndLogin() {
    service.signup(new SignupDto("t@wearly.test", "pass1234", "토큰"));
    return new LoginDto("t@wearly.test", "pass1234");
  }
}
