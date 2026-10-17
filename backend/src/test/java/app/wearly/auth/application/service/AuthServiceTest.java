package app.wearly.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.RefreshTokens;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.support.FakeUsers;
import app.wearly.support.InMemorySessionStore;
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
import org.junit.jupiter.api.Test;

/** 가입·로그인·세션·회전·정지 회원 처리 (가짜 포트, Spring 없이) */
class AuthServiceTest {

  static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  final FakeUsers users = new FakeUsers();
  final Map<Long, LocalCredential> credentialRows = new LinkedHashMap<>();
  final InMemorySessionStore store = new InMemorySessionStore();
  final List<AuthEvent> events = new ArrayList<>();
  final AuthEventPort eventPort = events::add;
  static final ClientDto PHONE = new ClientDto(null, "Wearly/1 CFNetwork/1 Darwin/24", "10.0.0.7");
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
        public String issue(long userId, String role, String sessionId) {
          return "access:" + userId + ":" + role + ":" + sessionId;
        }

        @Override
        public long accessTtlSeconds() {
          return 900;
        }
      };

  final SessionService sessions =
      new SessionService(store, eventPort, new TokenPolicy(Duration.ofDays(14)), clock);

  final AuthService service = new AuthService(users, credentials, sessions, issuer, hasher, clock);

  @Test
  void 가입하면_이메일을_정규화해_저장하고_토큰을_준다() {
    AuthResultDto r = service.signup(new SignupDto(" Kim@Wearly.Test ", "pass1234", " 김 "), PHONE);

    assertThat(r.user().email()).isEqualTo("kim@wearly.test");
    assertThat(r.user().name()).isEqualTo("김");
    assertThat(r.user().role()).isEqualTo(UserAccountDto.ROLE_USER);
    assertThat(r.accessToken()).startsWith("access:" + r.user().id() + ":USER:");
    assertThat(r.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(14).toSeconds());
    assertThat(credentialRows.values())
        .singleElement()
        .satisfies(c -> assertThat(c.passwordHash()).isEqualTo("hash:pass1234"));
    assertThat(store.sessions).hasSize(1);
    assertThat(r.accessToken()).endsWith(":" + store.sessions.keySet().iterator().next());

    assertThatThrownBy(
            () -> service.signup(new SignupDto("KIM@wearly.test", "x1234567", "다른"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(users.rows).hasSize(1);
  }

  @Test
  void 동시_가입으로_저장이_막히면_EMAIL_TAKEN() {
    concurrentSignup = true;

    assertThatThrownBy(
            () -> service.signup(new SignupDto("race@wearly.test", "pass1234", "경쟁"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(store.sessions).isEmpty();
  }

  @Test
  void 없는_이메일도_비밀번호_비교를_한_번_하고_같은_오류를_낸다() {
    service.signup(new SignupDto("a@wearly.test", "pass1234", "에이"), PHONE);
    checkedHashes.clear();

    assertThatThrownBy(() -> service.login(new LoginDto("none@wearly.test", "pass1234"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("a@wearly.test", "wrong"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThat(checkedHashes).containsExactly("hash:wearly-dummy-password-1", "hash:pass1234");
    assertThat(loginMarks).isEmpty();
  }

  @Test
  void 정지_회원은_비밀번호가_맞을_때만_정지를_알린다() {
    AuthResultDto r = service.signup(new SignupDto("s@wearly.test", "pass1234", "정지"), PHONE);
    users.changeAccess(r.user().id(), UserAccountDto.SUSPENDED, UserAccountDto.ROLE_USER);

    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "wrong"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "pass1234"), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_SUSPENDED));
    // 정지 회원의 Refresh 토큰도 받지 않는다
    assertThatThrownBy(() -> service.refresh(r.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    // 세션이 살아 있어도 정지 회원의 Access 토큰은 받지 않는다
    String sid = r.accessToken().substring(r.accessToken().lastIndexOf(':') + 1);
    assertThat(service.authenticate(r.user().id(), sid)).isEmpty();
  }

  @Test
  void 로그인하면_세션을_만들고_기기정보와_LOGIN을_남긴다() {
    service.signup(new SignupDto("c@wearly.test", "pass1234", "기기"), ClientDto.UNKNOWN);
    events.clear();
    AuthResultDto r =
        service.login(
            new LoginDto("c@wearly.test", "pass1234"),
            new ClientDto("admin", "Mozilla/5.0 Chrome", "203.0.113.9"));

    String sid = r.accessToken().substring(r.accessToken().lastIndexOf(':') + 1);
    var session = store.sessions.get(sid);
    assertThat(session.client()).isEqualTo(ClientKind.ADMIN);
    assertThat(session.ip()).isEqualTo("203.0.113.9");
    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.lastRefreshAt()).isNull();
    assertThat(r.accessToken()).endsWith(":" + session.id());
    assertThat(store.findToken(RefreshTokens.hash(r.refreshToken())).orElseThrow().sessionId())
        .isEqualTo(session.id());
    assertThat(events)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.type()).isEqualTo(AuthEvent.Type.LOGIN);
              assertThat(e.sessionId()).isEqualTo(session.id());
              assertThat(e.client()).isEqualTo(ClientKind.ADMIN);
            });
    assertThat(service.authenticate(r.user().id(), session.id())).contains("USER");
    assertThat(service.authenticate(r.user().id(), null)).isEmpty(); // sid 없는 옛 토큰
    assertThat(service.authenticate(r.user().id() + 1, session.id())).isEmpty(); // 남의 세션
  }

  @Test
  void 회전한_토큰을_다시_쓰면_세션을_끊고_REFRESH_REUSED를_남긴다() {
    AuthResultDto first = service.login(signupAndLogin(), PHONE);
    String sid =
        store.findToken(RefreshTokens.hash(first.refreshToken())).orElseThrow().sessionId();
    AuthResultDto second = service.refresh(first.refreshToken(), PHONE);
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(second.accessToken()).endsWith(":" + sid); // 회전해도 같은 세션
    assertThat(store.sessions.get(sid).lastRefreshAt()).isEqualTo(NOW);

    assertThatThrownBy(() -> service.refresh(first.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_REUSED));
    // 재사용을 감지하면 같은 세션의 최신 토큰도 못 쓰고 Access 토큰도 막힌다
    assertThatThrownBy(() -> service.refresh(second.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(service.authenticate(second.user().id(), sid)).isEmpty();
    assertThat(events.stream().map(AuthEvent::type))
        .containsSubsequence(AuthEvent.Type.LOGIN, AuthEvent.Type.REFRESH_REUSED);
  }

  @Test
  void 로그아웃하면_세션이_끊기고_LOGOUT을_남긴다_없는_토큰은_조용히() {
    AuthResultDto r = service.login(signupAndLogin(), PHONE);
    String sid = store.findToken(RefreshTokens.hash(r.refreshToken())).orElseThrow().sessionId();

    service.logout("unknown", PHONE);
    service.logout(null, PHONE);
    assertThat(store.sessions).containsKey(sid);

    service.logout(r.refreshToken(), PHONE);
    assertThat(store.sessions).doesNotContainKey(sid);
    assertThat(events.getLast().type()).isEqualTo(AuthEvent.Type.LOGOUT);
    assertThatThrownBy(() -> service.refresh(r.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(service.authenticate(r.user().id(), sid)).isEmpty();
  }

  @Test
  void 강제_로그아웃은_그_회원의_세션만_모두_끊는다() {
    final AuthResultDto b =
        service.signup(new SignupDto("other@wearly.test", "pass1234", "남"), PHONE);
    final AuthResultDto a = service.login(signupAndLogin(), PHONE); // 가입 + 로그인 = 세션 2개
    events.clear();

    assertThat(sessions.endUserSessions(a.user().id())).isEqualTo(2);
    assertThat(sessions.userSessions(a.user().id())).isEmpty();
    assertThat(sessions.userSessions(b.user().id())).hasSize(1);
    assertThat(events).hasSize(2).allMatch(e -> e.type() == AuthEvent.Type.FORCED_LOGOUT);
    assertThatThrownBy(() -> service.refresh(a.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(sessions.endSession("no-such-session")).isFalse();
  }

  @Test
  void 재인증은_빈_비밀번호도_더미_비교를_한다() {
    AuthResultDto r = service.signup(new SignupDto("r@wearly.test", "pass1234", "재인증"), PHONE);
    checkedHashes.clear();

    assertThat(service.passwordMatches(r.user().id(), "")).isFalse();
    assertThat(service.passwordMatches(999, "pass1234")).isFalse();
    assertThat(service.passwordMatches(r.user().id(), "pass1234")).isTrue();
    assertThat(checkedHashes)
        .containsExactly(
            "hash:wearly-dummy-password-1", "hash:wearly-dummy-password-1", "hash:pass1234");
  }

  private LoginDto signupAndLogin() {
    service.signup(new SignupDto("t@wearly.test", "pass1234", "토큰"), PHONE);
    return new LoginDto("t@wearly.test", "pass1234");
  }
}
