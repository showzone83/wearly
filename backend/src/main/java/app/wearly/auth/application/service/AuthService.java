package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.auth.application.port.in.AuthResult;
import app.wearly.auth.application.port.in.AuthUseCase;
import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.application.port.in.LoginCommand;
import app.wearly.auth.application.port.in.SignupCommand;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserAccount;
import app.wearly.user.application.port.in.UserUseCase;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements AuthUseCase, AuthenticationUseCase, AccountUseCase {

  private final UserUseCase users;
  private final CredentialPort credentials;
  private final RefreshTokenService refreshTokens;
  private final AccessTokenIssuer accessTokens;
  private final PasswordHasher passwords;
  private final Clock clock;

  /** 없는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 */
  private final String dummyHash;

  public AuthService(
      UserUseCase users,
      CredentialPort credentials,
      RefreshTokenService refreshTokens,
      AccessTokenIssuer accessTokens,
      PasswordHasher passwords,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.passwords = passwords;
    this.clock = clock;
    this.dummyHash = passwords.encode("wearly-dummy-password-1");
  }

  @Override
  @Transactional
  public AuthResult signup(SignupCommand req) {
    String email = LocalCredential.normalizeEmail(req.email());
    if (credentials.existsByEmail(email)) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN);
    }
    // 동시에 같은 이메일로 가입하면 자격 증명 저장에서 409 EMAIL_TAKEN (영속성 어댑터가 바꾼다)
    UserAccount user = users.create(email, req.name().strip(), UserAccount.ROLE_USER);
    credentials.create(
        new LocalCredential(null, user.id(), email, passwords.encode(req.password())));
    return issueTokens(user);
  }

  @Override
  @Transactional
  public AuthResult login(LoginCommand req) {
    Optional<LocalCredential> found =
        credentials.findByEmail(LocalCredential.normalizeEmail(req.email()));
    if (found.isEmpty()) {
      passwords.matches(req.password(), dummyHash);
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    LocalCredential credential = found.get();
    if (!passwords.matches(req.password(), credential.passwordHash())) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    UserAccount user = users.get(credential.userId());
    // 비밀번호가 맞을 때만 정지 여부를 알린다 (07 문서 2장)
    if (!user.isActive()) {
      throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
    }
    credentials.markLogin(credential.id(), clock.instant());
    return issueTokens(user);
  }

  @Override
  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult refresh(String rawRefreshToken) {
    RefreshTokenService.Issued next = refreshTokens.rotate(rawRefreshToken);
    UserAccount user =
        users
            .find(next.token().userId())
            .filter(UserAccount::isActive)
            .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));
    return toResult(user, next.rawToken());
  }

  @Override
  public void logout(String rawRefreshToken) {
    refreshTokens.revoke(rawRefreshToken);
  }

  @Override
  public Optional<String> activeUserRole(long userId) {
    return users.find(userId).filter(UserAccount::isActive).map(UserAccount::role);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findUserIdByEmail(String email) {
    return credentials.findByEmail(email).map(LocalCredential::userId);
  }

  @Override
  @Transactional
  public long createAccount(String email, String rawPassword, String name, String role) {
    UserAccount user = users.create(email, name, role);
    credentials.create(new LocalCredential(null, user.id(), email, passwords.encode(rawPassword)));
    return user.id();
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
  public void revokeAllRefreshTokens(long userId) {
    refreshTokens.revokeAllForUser(userId);
  }

  private AuthResult issueTokens(UserAccount user) {
    RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.id());
    return toResult(user, refresh.rawToken());
  }

  private AuthResult toResult(UserAccount user, String rawRefreshToken) {
    return new AuthResult(
        accessTokens.issue(user.id(), user.role()),
        accessTokens.accessTtlSeconds(),
        rawRefreshToken,
        refreshTokens.refreshTtlSeconds(),
        user);
  }
}
