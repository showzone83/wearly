package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.auth.application.port.in.AuthUseCase;
import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements AuthUseCase, AuthenticationUseCase, AccountUseCase {

  private final UserUseCase users;
  private final CredentialPort credentials;
  private final SessionService sessions;
  private final AccessTokenIssuer accessTokens;
  private final PasswordHasher passwords;
  private final Clock clock;

  /** 없는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 */
  private final String dummyHash;

  public AuthService(
      UserUseCase users,
      CredentialPort credentials,
      SessionService sessions,
      AccessTokenIssuer accessTokens,
      PasswordHasher passwords,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.sessions = sessions;
    this.accessTokens = accessTokens;
    this.passwords = passwords;
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
    return issueTokens(user, client);
  }

  @Override
  @Transactional
  public AuthResultDto login(LoginDto req, ClientDto client) {
    Optional<LocalCredential> found =
        credentials.findByEmail(LocalCredential.normalizeEmail(req.email()));
    if (found.isEmpty()) {
      passwords.matches(req.password(), dummyHash);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    LocalCredential credential = found.get();
    if (!passwords.matches(req.password(), credential.passwordHash())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    UserAccountDto user = users.get(credential.userId());
    // 비밀번호가 맞을 때만 정지 여부를 알린다 (07 문서 2장)
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
    }
    credentials.markLogin(credential.id(), clock.instant());
    return issueTokens(user, client);
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

  /** 세션 확인(Valkey)을 먼저 하고 회원 상태(DB)를 본다 */
  @Override
  public Optional<String> authenticate(long userId, String sessionId) {
    if (!sessions.isActive(userId, sessionId)) {
      return Optional.empty();
    }
    return users.find(userId).filter(UserAccountDto::isActive).map(UserAccountDto::role);
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
