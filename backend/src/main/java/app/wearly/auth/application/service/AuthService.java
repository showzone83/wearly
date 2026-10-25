package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.auth.application.port.in.AuthUseCase;
import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.AuthenticatedDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.MfaChallengeDto;
import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.application.port.out.SessionStorePort.SessionState;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.PasswordPolicy;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.invite.application.port.in.InviteUseCase;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements AuthUseCase, AuthenticationUseCase, AccountUseCase {

  private final UserUseCase users;
  private final CredentialPort credentials;
  private final SessionService sessions;
  private final MfaService mfa;
  private final AccessTokenIssuer accessTokens;
  private final PasswordHasher passwords;
  private final AuthEventPort events;
  private final InviteUseCase invites;
  private final Clock clock;

  /** 없는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 */
  private final String dummyHash;

  public AuthService(
      UserUseCase users,
      CredentialPort credentials,
      SessionService sessions,
      MfaService mfa,
      AccessTokenIssuer accessTokens,
      PasswordHasher passwords,
      AuthEventPort events,
      InviteUseCase invites,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.sessions = sessions;
    this.mfa = mfa;
    this.accessTokens = accessTokens;
    this.passwords = passwords;
    this.events = events;
    this.invites = invites;
    this.clock = clock;
    this.dummyHash = passwords.encode("wearly-dummy-password-1");
  }

  @Override
  @Transactional
  public AuthResultDto signup(SignupDto req, ClientDto client) {
    String email = LocalCredential.normalizeEmail(req.email());
    if (credentials.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_TAKEN);
    }
    UserAccountDto user = users.create(email, req.name().strip(), UserAccountDto.ROLE_USER);
    saveCredential(new LocalCredential(null, user.id(), email, passwords.encode(req.password())));
    // 초대 코드 가입 이력 (doc/25). 없는 코드여도 가입은 막지 않는다
    invites.recordSignup(user.id(), req.inviteCode());
    return issueTokens(user, client);
  }

  /** 비밀번호 실패 기록(LOGIN_FAILED)은 업무 오류로 끝나도 남겨야 하므로 롤백하지 않는다 */
  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public LoginResultDto login(LoginDto req, ClientDto client) {
    Optional<LocalCredential> found =
        credentials.findByEmail(LocalCredential.normalizeEmail(req.email()));
    if (found.isEmpty()) {
      passwords.matches(req.password(), dummyHash);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    LocalCredential credential = found.get();
    ClientInfo by = info(client);
    if (!passwords.matches(req.password(), credential.passwordHash())) {
      events.record(
          AuthEvent.of(
              credential.userId(), AuthEvent.Type.LOGIN_FAILED, null, by, clock.instant()));
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    UserAccountDto user = users.get(credential.userId());
    // 비밀번호가 맞을 때만 정지 여부를 알린다 (07 문서 2장)
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
    }
    // 관리자 사이트 로그인만 2단계 인증을 거친다. 앱·웹 로그인은 토큰을 주지만 그 세션으로는 관리자 API를 쓸 수 없다 (22 문서)
    if (user.isAdmin() && by.client() == ClientKind.ADMIN) {
      Optional<MfaChallengeDto> challenge = mfa.challenge(user, by);
      if (challenge.isPresent()) {
        return LoginResultDto.pending(challenge.get());
      }
    }
    credentials.markLogin(credential.id(), clock.instant());
    return LoginResultDto.of(issueTokens(user, client));
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public AuthResultDto refresh(String rawRefreshToken, ClientDto client) {
    SessionService.Rotated next = sessions.rotate(rawRefreshToken, info(client));
    UserAccountDto user =
        users
            .find(next.userId())
            .filter(UserAccountDto::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_INVALID));
    return toResult(user, next.rawToken(), next.sessionId());
  }

  @Override
  public void logout(String rawRefreshToken, ClientDto client) {
    sessions.logout(rawRefreshToken, info(client));
  }

  /**
   * 세션 확인(Valkey)을 먼저 하고 회원 상태(DB)를 본다. 관리자인데 2단계 인증을 확인한 세션이 아니면 그 관리자에게 2단계 인증이 필요한지(설정 또는 등록 여부)
   * 본다.
   */
  @Override
  public Optional<AuthenticatedDto> authenticate(long userId, String sessionId) {
    SessionState state = sessions.check(userId, sessionId);
    if (state == SessionState.GONE) {
      return Optional.empty();
    }
    return users
        .find(userId)
        .filter(UserAccountDto::isActive)
        .map(
            u ->
                new AuthenticatedDto(
                    u.role(),
                    u.isAdmin() && state != SessionState.ACTIVE_MFA && mfa.needed(userId)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findUserIdByEmail(String email) {
    return credentials.findByEmail(email).map(LocalCredential::userId);
  }

  @Override
  @Transactional
  public long createAccount(String email, String rawPassword, String name, String role) {
    UserAccountDto user = users.create(email, name, role);
    saveCredential(new LocalCredential(null, user.id(), email, passwords.encode(rawPassword)));
    return user.id();
  }

  /** 동시에 같은 이메일로 가입하면 고유 제약에 걸린다 → 409 EMAIL_TAKEN (트랜잭션은 롤백되어 회원도 남지 않는다) */
  private void saveCredential(LocalCredential credential) {
    try {
      credentials.create(credential);
    } catch (EmailTakenException e) {
      throw new BusinessException(ErrorCode.EMAIL_TAKEN);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean passwordMatches(long userId, String rawPassword) {
    Optional<LocalCredential> found = credentials.findByUserId(userId);
    if (rawPassword == null || rawPassword.isEmpty() || found.isEmpty()) {
      passwords.matches("wearly-reauth-dummy", dummyHash);
      return false;
    }
    return passwords.matches(rawPassword, found.get().passwordHash());
  }

  @Override
  @Transactional
  public int changePassword(
      long userId, String keepSessionId, String currentPassword, String newPassword) {
    List<FieldError> errors = new ArrayList<>();
    if (currentPassword == null || currentPassword.isEmpty()) {
      errors.add(FieldError.required("currentPassword"));
    }
    if (newPassword == null || newPassword.isEmpty()) {
      errors.add(FieldError.required("newPassword"));
    } else if (!PasswordPolicy.isValid(newPassword)) {
      errors.add(FieldError.invalidFormat("newPassword"));
    }
    BusinessException.throwIfAny(errors);
    if (!passwordMatches(userId, currentPassword)) {
      throw new BusinessException(ErrorCode.REAUTH_FAILED);
    }
    LocalCredential credential =
        credentials.findByUserId(userId).orElseThrow(BusinessException::notFound);
    credentials.changePassword(credential.id(), passwords.encode(newPassword));
    return sessions.endOtherSessions(userId, keepSessionId);
  }

  /** 새 로그인 세션을 만들고 토큰을 준다 (LOGIN 기록) */
  private AuthResultDto issueTokens(UserAccountDto user, ClientDto client) {
    SessionService.Issued issued = sessions.start(user.id(), info(client));
    return toResult(user, issued.rawToken(), issued.sessionId());
  }

  private AuthResultDto toResult(UserAccountDto user, String rawRefreshToken, String sessionId) {
    return new AuthResultDto(
        accessTokens.issue(user.id(), user.role(), sessionId),
        accessTokens.accessTtlSeconds(),
        rawRefreshToken,
        sessions.refreshTtlSeconds(),
        user);
  }

  private static ClientInfo info(ClientDto c) {
    ClientDto v = c == null ? ClientDto.UNKNOWN : c;
    return ClientInfo.of(v.clientHeader(), v.userAgent(), v.ip());
  }
}
