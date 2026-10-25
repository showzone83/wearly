package app.wearly.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.AuthenticatedDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyResultDto;
import app.wearly.auth.application.port.in.dto.PasswordStatusDto;
import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.MfaPolicy;
import app.wearly.auth.domain.PasswordChangePolicy;
import app.wearly.auth.domain.RefreshTokens;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.support.FakeInvites;
import app.wearly.support.FakeUsers;
import app.wearly.support.FakeWithdrawals;
import app.wearly.support.InMemoryMfa;
import app.wearly.support.InMemorySessionStore;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import app.wearly.user.application.port.in.dto.WithdrawDto;
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
          credentialRows.put(
              id, new LocalCredential(id, c.userId(), c.email(), c.passwordHash(), NOW, null));
        }

        @Override
        public void markLogin(long credentialId, Instant at) {
          loginMarks.add(credentialId);
        }

        @Override
        public void changePassword(long credentialId, String passwordHash, Instant at) {
          LocalCredential c = credentialRows.get(credentialId);
          credentialRows.put(
              credentialId,
              new LocalCredential(c.id(), c.userId(), c.email(), passwordHash, at, null));
        }

        @Override
        public void postpone(long credentialId, Instant at) {
          credentialRows.put(credentialId, credentialRows.get(credentialId).postponedAt(at));
        }

        @Override
        public List<Long> findReminderDueUserIds(Instant dueBy, int limit) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean claimReminder(long userId, Instant dueBy, Instant now) {
          throw new UnsupportedOperationException();
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

  final InMemoryMfa mfaStore = new InMemoryMfa();

  /** 관리자 2단계 인증 필수 (설정 wearly.admin.mfa.required 기본값) */
  final MfaService mfa =
      new MfaService(
          users,
          mfaStore,
          mfaStore,
          mfaStore.cipher,
          mfaStore.totp,
          sessions,
          issuer,
          credentials,
          hasher,
          eventPort,
          new MfaPolicy(true, null),
          clock);

  final FakeInvites invites = new FakeInvites();

  final FakeWithdrawals withdrawals = new FakeWithdrawals(users);

  final AuthService service =
      new AuthService(
          users,
          credentials,
          sessions,
          mfa,
          issuer,
          hasher,
          eventPort,
          invites,
          withdrawals,
          clock);

  final PasswordService passwords =
      new PasswordService(
          credentials,
          service,
          hasher,
          sessions,
          eventPort,
          new PasswordChangePolicy(Duration.ofDays(90), 500),
          clock);

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
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(users.rows).hasSize(1);
  }

  @Test
  void 가입하면_입력한_초대_코드를_초대_컨텍스트에_넘긴다() {
    AuthResultDto a =
        service.signup(new SignupDto("i@wearly.test", "pass1234", "초대", "abcd-efgh"), PHONE);
    AuthResultDto b = service.signup(new SignupDto("n@wearly.test", "pass1234", "없음"), PHONE);

    // 코드 정리·추천인 찾기·없는 코드 처리는 초대 컨텍스트가 한다 (가입은 막지 않는다)
    assertThat(invites.signups)
        .containsExactly(
            new FakeInvites.Signup(a.user().id(), "abcd-efgh"),
            new FakeInvites.Signup(b.user().id(), null));
  }

  @Test
  void 동시_가입으로_저장이_막히면_EMAIL_TAKEN() {
    concurrentSignup = true;

    assertThatThrownBy(
            () -> service.signup(new SignupDto("race@wearly.test", "pass1234", "경쟁"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    assertThat(store.sessions).isEmpty();
  }

  @Test
  void 없는_이메일도_비밀번호_비교를_한_번_하고_같은_오류를_낸다() {
    service.signup(new SignupDto("a@wearly.test", "pass1234", "에이"), PHONE);
    checkedHashes.clear();

    assertThatThrownBy(() -> service.login(new LoginDto("none@wearly.test", "pass1234"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("a@wearly.test", "wrong"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThat(checkedHashes).containsExactly("hash:wearly-dummy-password-1", "hash:pass1234");
    assertThat(loginMarks).isEmpty();
    // 가입한 이메일의 비밀번호 실패만 로그인 이력에 남긴다 (없는 이메일은 남기지 않는다)
    assertThat(events.stream().filter(e -> e.type() == AuthEvent.Type.LOGIN_FAILED))
        .singleElement()
        .satisfies(e -> assertThat(e.sessionId()).isNull());
  }

  @Test
  void 탈퇴_예정_회원은_비밀번호가_맞을_때만_알리고_취소를_고르면_되돌려_로그인한다() {
    AuthResultDto r = service.signup(new SignupDto("w@wearly.test", "pass1234", "탈퇴"), PHONE);
    withdrawals.withdraw(r.user().id(), new WithdrawDto("OTHER", null));

    assertThatThrownBy(() -> service.login(new LoginDto("w@wearly.test", "wrong"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("w@wearly.test", "pass1234"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
              assertThat(e.params()).containsEntry("purgeAt", FakeWithdrawals.PURGE.toString());
            });
    assertThat(withdrawals.canceled).isEmpty();

    AuthResultDto back =
        service.login(new LoginDto("w@wearly.test", "pass1234", true), PHONE).tokens();
    assertThat(back.user().id()).isEqualTo(r.user().id());
    assertThat(users.get(r.user().id()).isActive()).isTrue();
    assertThat(withdrawals.canceled).containsEntry(r.user().id(), false);
  }

  @Test
  void 탈퇴_취소_표시는_탈퇴_예정이_아니면_아무것도_하지_않는다() {
    service.signup(new SignupDto("n@wearly.test", "pass1234", "보통"), PHONE);
    assertThat(service.login(new LoginDto("n@wearly.test", "pass1234", true), PHONE).tokens())
        .isNotNull();
    assertThat(withdrawals.canceled).isEmpty();
  }

  @Test
  void 정지_회원은_비밀번호가_맞을_때만_정지를_알린다() {
    AuthResultDto r = service.signup(new SignupDto("s@wearly.test", "pass1234", "정지"), PHONE);
    users.changeAccess(r.user().id(), UserAccountDto.SUSPENDED, UserAccountDto.ROLE_USER);

    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "wrong"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThatThrownBy(() -> service.login(new LoginDto("s@wearly.test", "pass1234"), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_SUSPENDED));
    // 정지 회원의 Refresh 토큰도 받지 않는다
    assertThatThrownBy(() -> service.refresh(r.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    // 세션이 살아 있어도 정지 회원의 Access 토큰은 받지 않는다
    String sid = r.accessToken().substring(r.accessToken().lastIndexOf(':') + 1);
    assertThat(service.authenticate(r.user().id(), sid)).isEmpty();
  }

  @Test
  void 로그인하면_세션을_만들고_기기정보와_LOGIN을_남긴다() {
    service.signup(new SignupDto("c@wearly.test", "pass1234", "기기"), ClientDto.UNKNOWN);
    events.clear();
    AuthResultDto r =
        service
            .login(
                new LoginDto("c@wearly.test", "pass1234"),
                new ClientDto("admin", "Mozilla/5.0 Chrome", "203.0.113.9"))
            .tokens();

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
    assertThat(service.authenticate(r.user().id(), session.id()))
        .contains(new AuthenticatedDto("USER", false));
    assertThat(service.authenticate(r.user().id(), null)).isEmpty(); // sid 없는 옛 토큰
    assertThat(service.authenticate(r.user().id() + 1, session.id())).isEmpty(); // 남의 세션
  }

  @Test
  void 회전한_토큰을_다시_쓰면_세션을_끊고_REFRESH_REUSED를_남긴다() {
    AuthResultDto first = service.login(signupAndLogin(), PHONE).tokens();
    String sid =
        store.findToken(RefreshTokens.hash(first.refreshToken())).orElseThrow().sessionId();
    AuthResultDto second = service.refresh(first.refreshToken(), PHONE);
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(second.accessToken()).endsWith(":" + sid); // 회전해도 같은 세션
    assertThat(store.sessions.get(sid).lastRefreshAt()).isEqualTo(NOW);

    assertThatThrownBy(() -> service.refresh(first.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_REUSED));
    // 재사용을 감지하면 같은 세션의 최신 토큰도 못 쓰고 Access 토큰도 막힌다
    assertThatThrownBy(() -> service.refresh(second.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(service.authenticate(second.user().id(), sid)).isEmpty();
    assertThat(events.stream().map(AuthEvent::type))
        .containsSubsequence(AuthEvent.Type.LOGIN, AuthEvent.Type.REFRESH_REUSED);
  }

  @Test
  void 로그아웃하면_세션이_끊기고_LOGOUT을_남긴다_없는_토큰은_조용히() {
    AuthResultDto r = service.login(signupAndLogin(), PHONE).tokens();
    String sid = store.findToken(RefreshTokens.hash(r.refreshToken())).orElseThrow().sessionId();

    service.logout("unknown", PHONE);
    service.logout(null, PHONE);
    assertThat(store.sessions).containsKey(sid);

    service.logout(r.refreshToken(), PHONE);
    assertThat(store.sessions).doesNotContainKey(sid);
    assertThat(events.getLast().type()).isEqualTo(AuthEvent.Type.LOGOUT);
    assertThatThrownBy(() -> service.refresh(r.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
    assertThat(service.authenticate(r.user().id(), sid)).isEmpty();
  }

  @Test
  void 강제_로그아웃은_그_회원의_세션만_모두_끊는다() {
    final AuthResultDto b =
        service.signup(new SignupDto("other@wearly.test", "pass1234", "남"), PHONE);
    final AuthResultDto a = service.login(signupAndLogin(), PHONE).tokens(); // 가입 + 로그인 = 세션 2개
    events.clear();

    assertThat(sessions.endUserSessions(a.user().id())).isEqualTo(2);
    assertThat(sessions.userSessions(a.user().id())).isEmpty();
    assertThat(sessions.userSessions(b.user().id())).hasSize(1);
    assertThat(events).hasSize(2).allMatch(e -> e.type() == AuthEvent.Type.FORCED_LOGOUT);
    assertThatThrownBy(() -> service.refresh(a.refreshToken(), PHONE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.REFRESH_INVALID));
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

  // ── 관리자 2단계 인증 (22 문서) ──

  static final ClientDto ADMIN_SITE = new ClientDto("admin", "Mozilla/5.0 Chrome", "203.0.113.9");

  /** 관리자 계정 (자격 증명 포함) */
  private long admin(String email) {
    return service.createAccount(email, "pass1234", "관리자", UserAccountDto.ROLE_ADMIN);
  }

  private static String sid(AuthResultDto r) {
    return r.accessToken().substring(r.accessToken().lastIndexOf(':') + 1);
  }

  private static ErrorCode codeOf(Runnable call) {
    try {
      call.run();
    } catch (BusinessException e) {
      return e.code();
    }
    throw new AssertionError("BusinessException이 나지 않았어요");
  }

  @Test
  void 관리자_사이트_로그인은_대기_토큰을_주고_앱_로그인_세션은_관리자_API를_못_쓴다() {
    long id = admin("m1@wearly.test");

    LoginResultDto site = service.login(new LoginDto("m1@wearly.test", "pass1234"), ADMIN_SITE);
    assertThat(site.tokens()).isNull();
    assertThat(site.mfa().enrolled()).isFalse();
    assertThat(site.mfa().expiresInSeconds()).isEqualTo(300);
    assertThat(store.sessions).isEmpty(); // 대기 중에는 세션이 없다
    assertThat(loginMarks).isEmpty();

    // 앱 로그인은 예전처럼 토큰을 주지만, 그 세션은 2단계 인증을 거치지 않았다
    AuthResultDto app = service.login(new LoginDto("m1@wearly.test", "pass1234"), PHONE).tokens();
    assertThat(service.authenticate(id, sid(app)))
        .contains(new AuthenticatedDto(UserAccountDto.ROLE_ADMIN, true));
  }

  @Test
  void 등록하면_복구_코드를_주고_같은_코드는_다시_못_쓴다() {
    long id = admin("m2@wearly.test");
    String token =
        service.login(new LoginDto("m2@wearly.test", "pass1234"), ADMIN_SITE).mfa().mfaToken();

    // 등록 시작 없이 코드를 보내면 409
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(token, "000100", null), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_SETUP_REQUIRED);
    assertThat(mfa.setup(token).otpauthUrl()).contains("m2@wearly.test");
    MfaVerifyResultDto first = mfa.verify(new MfaVerifyDto(token, "000100", null), ADMIN_SITE);
    assertThat(first.recoveryCodes()).hasSize(10).doesNotHaveDuplicates();
    assertThat(first.recoveryCodesLeft()).isEqualTo(10);
    assertThat(mfaStore.enrollments.get(id).lastUsedStep()).isEqualTo(100);
    assertThat(service.authenticate(id, sid(first.tokens())))
        .contains(new AuthenticatedDto(UserAccountDto.ROLE_ADMIN, false));
    assertThat(events.stream().map(AuthEvent::type))
        .containsSubsequence(AuthEvent.Type.LOGIN, AuthEvent.Type.MFA_SUCCESS);
    // 대기 토큰은 한 번만 쓴다
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(token, "000101", null), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_TOKEN_INVALID);

    // 다음 로그인: 등록됨. 같은 단계의 코드(재사용)는 틀린 코드로 센다
    String next =
        service.login(new LoginDto("m2@wearly.test", "pass1234"), ADMIN_SITE).mfa().mfaToken();
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(next, "000100", null), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_CODE_INVALID);
    assertThatThrownBy(() -> mfa.setup(next))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.MFA_ALREADY_ENROLLED));
    String recovery = first.recoveryCodes().get(3);
    MfaVerifyResultDto viaRecovery =
        mfa.verify(new MfaVerifyDto(next, null, recovery.toUpperCase()), ADMIN_SITE);
    assertThat(viaRecovery.recoveryCodes()).isNull();
    assertThat(viaRecovery.recoveryCodesLeft()).isEqualTo(9);
    assertThat(mfaStore.failures).doesNotContainKey(id); // 성공하면 틀린 횟수를 지운다

    // 쓴 복구 코드는 다시 못 쓴다
    String again =
        service.login(new LoginDto("m2@wearly.test", "pass1234"), ADMIN_SITE).mfa().mfaToken();
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(again, null, recovery), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_CODE_INVALID);
    assertThat(mfa.verify(new MfaVerifyDto(again, "000101", null), ADMIN_SITE).tokens())
        .isNotNull();
  }

  @Test
  void 다섯_번_틀리면_잠그고_대기_토큰을_지운다() {
    final long id = admin("m3@wearly.test");
    String token =
        service.login(new LoginDto("m3@wearly.test", "pass1234"), ADMIN_SITE).mfa().mfaToken();
    mfa.setup(token);
    events.clear();

    for (int i = 1; i <= 4; i++) {
      final int tried = i;
      assertThatThrownBy(() -> mfa.verify(new MfaVerifyDto(token, "999999", null), ADMIN_SITE))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.MFA_CODE_INVALID);
                assertThat(e.params()).containsEntry("remainingAttempts", 5L - tried);
              });
    }
    assertThatThrownBy(() -> mfa.verify(new MfaVerifyDto(token, "999999", null), ADMIN_SITE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.MFA_LOCKED);
              assertThat(e.params()).containsEntry("retryAfterSeconds", 900L);
            });
    assertThat(mfaStore.pending).isEmpty();
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(token, "000100", null), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_TOKEN_INVALID);
    // 잠긴 동안에는 비밀번호가 맞아도 대기 토큰을 주지 않는다
    assertThat(codeOf(() -> service.login(new LoginDto("m3@wearly.test", "pass1234"), ADMIN_SITE)))
        .isEqualTo(ErrorCode.MFA_LOCKED);
    assertThat(events.stream().map(AuthEvent::type))
        .containsExactly(
            AuthEvent.Type.MFA_FAILED,
            AuthEvent.Type.MFA_FAILED,
            AuthEvent.Type.MFA_FAILED,
            AuthEvent.Type.MFA_FAILED,
            AuthEvent.Type.MFA_FAILED,
            AuthEvent.Type.MFA_LOCKED);
    assertThat(events).allMatch(e -> e.userId() == id && e.client() == ClientKind.ADMIN);
    // 형식이 틀린 입력은 횟수에 넣지 않고 400
    mfaStore.locks.clear();
    String next =
        service.login(new LoginDto("m3@wearly.test", "pass1234"), ADMIN_SITE).mfa().mfaToken();
    assertThat(codeOf(() -> mfa.verify(new MfaVerifyDto(next, "12ab", null), ADMIN_SITE)))
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(mfaStore.failures).doesNotContainKey(id);
  }

  @Test
  void 비밀번호를_바꾸면_다른_세션만_끊고_변경_주기를_다시_센다() {
    AuthResultDto keep = service.signup(new SignupDto("p@wearly.test", "pass1234", "비번"), PHONE);
    final AuthResultDto other =
        service.login(new LoginDto("p@wearly.test", "pass1234"), PHONE).tokens();
    long id = keep.user().id();
    long credentialId = credentialRows.keySet().iterator().next();
    // 오래전에 정하고 연장까지 한 비밀번호
    credentialRows.put(
        credentialId,
        new LocalCredential(
            credentialId,
            id,
            "p@wearly.test",
            "hash:pass1234",
            NOW.minus(Duration.ofDays(200)),
            NOW.minus(Duration.ofDays(100))));
    assertThat(passwords.status(id).due()).isTrue();

    assertThat(codeOf(() -> passwords.change(id, sid(keep), "wrong", "newpass99")))
        .isEqualTo(ErrorCode.REAUTH_FAILED);
    assertThat(codeOf(() -> passwords.change(id, sid(keep), "pass1234", "short")))
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    events.clear();
    assertThat(passwords.change(id, sid(keep), "pass1234", "newpass99")).isEqualTo(1);

    assertThat(service.authenticate(id, sid(keep))).isPresent();
    assertThat(service.authenticate(id, sid(other))).isEmpty();
    assertThat(service.passwordMatches(id, "newpass99")).isTrue();
    LocalCredential changed = credentialRows.get(credentialId);
    assertThat(changed.passwordChangedAt()).isEqualTo(NOW);
    assertThat(changed.passwordPostponedAt()).isNull();
    assertThat(passwords.status(id))
        .isEqualTo(new PasswordStatusDto(NOW, NOW.plus(Duration.ofDays(90)), false, 90));
    assertThat(events)
        .extracting(AuthEvent::type)
        .containsExactly(AuthEvent.Type.PASSWORD_CHANGED, AuthEvent.Type.FORCED_LOGOUT);
    assertThat(events.get(0).sessionId()).isEqualTo(sid(keep));
    assertThat(events.get(0).userAgent()).isEqualTo(PHONE.userAgent());
  }

  @Test
  void 지금과_같은_비밀번호로는_못_바꾼다() {
    AuthResultDto me = service.signup(new SignupDto("s@wearly.test", "pass1234", "같은"), PHONE);
    long id = me.user().id();

    assertThatThrownBy(() -> passwords.change(id, sid(me), "pass1234", "pass1234"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.fieldErrors())
                  .singleElement()
                  .satisfies(
                      f -> {
                        assertThat(f.field()).isEqualTo("newPassword");
                        assertThat(f.code()).isEqualTo(FieldErrorCode.NOT_ALLOWED);
                      });
            });
    assertThat(service.passwordMatches(id, "pass1234")).isTrue();
  }

  @Test
  void 연장하면_지금부터_주기만큼_미루고_PASSWORD_POSTPONED를_남긴다() {
    AuthResultDto me = service.signup(new SignupDto("d@wearly.test", "pass1234", "연장"), PHONE);
    long id = me.user().id();
    long credentialId = credentialRows.keySet().iterator().next();
    credentialRows.put(
        credentialId,
        new LocalCredential(
            credentialId,
            id,
            "d@wearly.test",
            "hash:pass1234",
            NOW.minus(Duration.ofDays(91)),
            null));
    assertThat(passwords.status(id))
        .isEqualTo(
            new PasswordStatusDto(
                NOW.minus(Duration.ofDays(91)), NOW.minus(Duration.ofDays(1)), true, 90));
    events.clear();

    PasswordStatusDto after = passwords.postpone(id, sid(me));

    assertThat(after.due()).isFalse();
    assertThat(after.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
    assertThat(passwords.status(id)).isEqualTo(after);
    assertThat(events)
        .extracting(AuthEvent::type)
        .containsExactly(AuthEvent.Type.PASSWORD_POSTPONED);
  }

  @Test
  void 비밀번호가_없거나_기능이_꺼졌으면_연장할_수_없다() {
    assertThat(passwords.status(999L)).isEqualTo(new PasswordStatusDto(null, null, false, 90));
    assertThat(codeOf(() -> passwords.postpone(999L, null))).isEqualTo(ErrorCode.NOT_FOUND);

    AuthResultDto me = service.signup(new SignupDto("o@wearly.test", "pass1234", "끔"), PHONE);
    PasswordService off =
        new PasswordService(
            credentials,
            service,
            hasher,
            sessions,
            eventPort,
            new PasswordChangePolicy(Duration.ZERO, 500),
            clock);
    assertThat(off.status(me.user().id())).isEqualTo(new PasswordStatusDto(NOW, null, false, 0));
    assertThat(codeOf(() -> off.postpone(me.user().id(), sid(me)))).isEqualTo(ErrorCode.NOT_FOUND);
  }

  private LoginDto signupAndLogin() {
    service.signup(new SignupDto("t@wearly.test", "pass1234", "토큰"), PHONE);
    return new LoginDto("t@wearly.test", "pass1234");
  }
}
