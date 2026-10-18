package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.MfaUseCase;
import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.MfaChallengeDto;
import app.wearly.auth.application.port.in.dto.MfaSetupDto;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyResultDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.MfaChallengeStorePort;
import app.wearly.auth.application.port.out.MfaPort;
import app.wearly.auth.application.port.out.MfaSecretCipherPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.application.port.out.TotpPort;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.MfaEnrollment;
import app.wearly.auth.domain.MfaPolicy;
import app.wearly.auth.domain.MfaSecretUnavailableException;
import app.wearly.auth.domain.PendingMfa;
import app.wearly.auth.domain.RecoveryCodes;
import app.wearly.auth.domain.RefreshTokens;
import app.wearly.auth.domain.SealedMfaSecret;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 2단계 인증 TOTP (22 문서 4.1).
 *
 * <ul>
 *   <li>관리자 사이트 로그인(X-Wearly-Client: admin)에서 비밀번호가 맞고 2단계 인증이 필요하면({@link MfaPolicy#needed}) 토큰 대신
 *       MFA 대기 토큰(5분)을 준다 → setup(등록 전) → verify → 2단계 인증을 확인한 세션과 정식 토큰.
 *   <li>비밀은 마스터 키로 암호화해 DB(등록)·Valkey(등록 중)에 둔다. 같은 30초 단계는 한 번만 받는다(재사용 막기).
 *   <li>코드를 5번 틀리면 15분 잠그고 대기 토큰을 지운다. 틀린 횟수는 회원별이라 다시 로그인해도 이어진다.
 *   <li>틀림·잠김·성공은 로그인 이력(MFA_FAILED·MFA_LOCKED·MFA_SUCCESS)에 남는다. 그래서 업무 오류로 끝나도 롤백하지 않는다.
 * </ul>
 */
@Service
public class MfaService implements MfaUseCase {

  private static final Logger log = LoggerFactory.getLogger(MfaService.class);

  private final UserUseCase users;
  private final MfaPort mfa;
  private final MfaChallengeStorePort store;
  private final MfaSecretCipherPort cipher;
  private final TotpPort totp;
  private final SessionService sessions;
  private final AccessTokenIssuer accessTokens;
  private final CredentialPort credentials;
  private final PasswordHasher passwords;
  private final AuthEventPort events;
  private final MfaPolicy policy;
  private final Clock clock;

  /** 비밀번호 재확인에서 자격 증명이 없을 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 */
  private final String dummyHash;

  public MfaService(
      UserUseCase users,
      MfaPort mfa,
      MfaChallengeStorePort store,
      MfaSecretCipherPort cipher,
      TotpPort totp,
      SessionService sessions,
      AccessTokenIssuer accessTokens,
      CredentialPort credentials,
      PasswordHasher passwords,
      AuthEventPort events,
      MfaPolicy policy,
      Clock clock) {
    this.users = users;
    this.mfa = mfa;
    this.store = store;
    this.cipher = cipher;
    this.totp = totp;
    this.sessions = sessions;
    this.accessTokens = accessTokens;
    this.credentials = credentials;
    this.passwords = passwords;
    this.events = events;
    this.policy = policy;
    this.clock = clock;
    this.dummyHash = passwords.encode("wearly-mfa-dummy-password-1");
  }

  // ── 로그인 (AuthService가 부른다) ──

  /** 이 관리자에게 2단계 인증이 필요한지 (설정 required이거나 이미 등록) */
  public boolean needed(long userId) {
    return policy.required() || mfa.exists(userId);
  }

  /** 비밀번호가 맞은 관리자 로그인: 2단계 인증이 필요하면 대기 토큰을 만든다. 잠겨 있으면 429 MFA_LOCKED. 필요 없으면 빈 값(바로 토큰). */
  public Optional<MfaChallengeDto> challenge(UserAccountDto user, ClientInfo client) {
    boolean enrolled = mfa.exists(user.id());
    if (!policy.needed(enrolled)) {
      return Optional.empty();
    }
    ensureNotLocked(user.id());
    String raw = RefreshTokens.newRawToken();
    store.savePending(
        RefreshTokens.hash(raw),
        new PendingMfa(user.id(), client, clock.instant()),
        MfaPolicy.PENDING_TTL);
    return Optional.of(new MfaChallengeDto(raw, MfaPolicy.PENDING_TTL.toSeconds(), enrolled));
  }

  @Override
  public MfaSetupDto setup(String mfaToken) {
    Pending p = pending(mfaToken);
    ensureNotLocked(p.user().id());
    if (mfa.exists(p.user().id())) {
      throw new BusinessException(ErrorCode.MFA_ALREADY_ENROLLED);
    }
    return newSetup(p.user());
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public MfaVerifyResultDto verify(MfaVerifyDto command, ClientDto client) {
    Answer answer = Answer.of(command.code(), command.recoveryCode());
    Pending p = pending(command.mfaToken());
    UserAccountDto user = p.user();
    ClientInfo by = info(client);
    try {
      ensureNotLocked(user.id());
    } catch (BusinessException e) {
      store.deletePending(p.tokenHash());
      throw e;
    }
    Optional<MfaEnrollment> enrollment = mfa.find(user.id());
    List<String> newCodes = null;
    boolean ok;
    if (enrollment.isPresent()) {
      ok = check(enrollment.get(), answer);
    } else {
      newCodes = enroll(user.id(), answer);
      ok = newCodes != null;
    }
    if (!ok) {
      throw failed(user.id(), by, p.tokenHash());
    }
    store.deletePending(p.tokenHash());
    store.clearFailures(user.id());
    SessionService.Issued issued = sessions.start(user.id(), by, true);
    events.record(
        AuthEvent.of(user.id(), AuthEvent.Type.MFA_SUCCESS, issued.sessionId(), by, now()));
    credentials.findByUserId(user.id()).ifPresent(c -> credentials.markLogin(c.id(), now()));
    AuthResultDto tokens =
        new AuthResultDto(
            accessTokens.issue(user.id(), user.role(), issued.sessionId()),
            accessTokens.accessTtlSeconds(),
            issued.rawToken(),
            sessions.refreshTtlSeconds(),
            user);
    return new MfaVerifyResultDto(tokens, newCodes, mfa.countRecoveryCodes(user.id()));
  }

  // ── 로그인한 관리자 ──

  @Override
  @Transactional(readOnly = true)
  public MfaStatusDto status(long userId) {
    Optional<MfaEnrollment> e = mfa.find(userId);
    return new MfaStatusDto(
        e.isPresent(),
        e.map(MfaEnrollment::enrolledAt).orElse(null),
        e.isPresent() ? mfa.countRecoveryCodes(userId) : 0,
        policy.required());
  }

  @Override
  public MfaSetupDto startSetup(long userId) {
    UserAccountDto user = users.get(userId);
    if (mfa.exists(userId)) {
      throw new BusinessException(ErrorCode.MFA_ALREADY_ENROLLED);
    }
    return newSetup(user);
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public List<String> enable(long userId, String sessionId, String code) {
    Answer answer = Answer.of(code, null);
    ensureNotLocked(userId);
    if (mfa.exists(userId)) {
      throw new BusinessException(ErrorCode.MFA_ALREADY_ENROLLED);
    }
    ClientInfo by = clientOf(sessionId);
    List<String> codes = enroll(userId, answer);
    if (codes == null) {
      throw failed(userId, by, null);
    }
    store.clearFailures(userId);
    sessions.markMfaVerified(userId, sessionId);
    events.record(AuthEvent.of(userId, AuthEvent.Type.MFA_SUCCESS, sessionId, by, now()));
    return codes;
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public int reset(
      long userId, String sessionId, String currentPassword, String code, String recoveryCode) {
    List<FieldError> errors = new ArrayList<>();
    if (currentPassword == null || currentPassword.isEmpty()) {
      errors.add(FieldError.required("currentPassword"));
    }
    Answer answer;
    try {
      answer = Answer.of(code, recoveryCode);
    } catch (BusinessException e) {
      errors.addAll(e.fieldErrors());
      answer = null;
    }
    BusinessException.throwIfAny(errors);
    if (!passwordMatches(userId, currentPassword)) {
      throw new BusinessException(ErrorCode.REAUTH_FAILED);
    }
    ensureNotLocked(userId);
    MfaEnrollment enrollment =
        mfa.find(userId).orElseThrow(() -> new BusinessException(ErrorCode.MFA_NOT_ENROLLED));
    ClientInfo by = clientOf(sessionId);
    if (!check(enrollment, answer)) {
      throw failed(userId, by, null);
    }
    store.clearFailures(userId);
    events.record(AuthEvent.of(userId, AuthEvent.Type.MFA_SUCCESS, sessionId, by, now()));
    mfa.delete(userId);
    store.deleteSetupSecret(userId);
    return sessions.endOtherSessions(userId, sessionId);
  }

  @Override
  @Transactional
  public MfaStatusDto remove(long userId) {
    final MfaStatusDto before = status(userId);
    mfa.delete(userId);
    store.deleteSetupSecret(userId);
    store.clearFailures(userId);
    return before;
  }

  // ── 내부 ──

  /** 대기 토큰으로 찾은 회원. 없거나 만료됐거나 그사이 쓸 수 없는 관리자가 되면 401 MFA_TOKEN_INVALID */
  private Pending pending(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new BusinessException(ErrorCode.MFA_TOKEN_INVALID);
    }
    String hash = RefreshTokens.hash(rawToken);
    PendingMfa p =
        store
            .findPending(hash)
            .orElseThrow(() -> new BusinessException(ErrorCode.MFA_TOKEN_INVALID));
    Optional<UserAccountDto> user = users.find(p.userId()).filter(u -> u.isActive() && u.isAdmin());
    if (user.isEmpty()) {
      store.deletePending(hash);
      throw new BusinessException(ErrorCode.MFA_TOKEN_INVALID);
    }
    return new Pending(hash, user.get());
  }

  private MfaSetupDto newSetup(UserAccountDto user) {
    if (!cipher.enabled()) {
      log.warn("2단계 인증 등록 불가: wearly.secret-key(마스터 키)가 없어요. userId={}", user.id());
      throw new BusinessException(ErrorCode.SECRET_KEY_MISSING);
    }
    String secret = totp.newSecret();
    store.saveSetupSecret(
        user.id(), cipher.seal(secret, MfaEnrollment.aad(user.id())), MfaPolicy.SETUP_TTL);
    String account = user.email() == null ? "user-" + user.id() : user.email();
    return new MfaSetupDto(secret, totp.otpauthUrl(policy.issuer(), account, secret));
  }

  /**
   * 등록 중인 비밀로 코드를 확인하고 맞으면 등록을 마친다.
   *
   * @return 새 복구 코드 (코드가 틀리면 null)
   */
  private List<String> enroll(long userId, Answer answer) {
    if (answer.recoveryCode() != null) {
      throw new BusinessException(ErrorCode.MFA_SETUP_REQUIRED);
    }
    SealedMfaSecret sealed =
        store
            .findSetupSecret(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MFA_SETUP_REQUIRED));
    OptionalLong step = totp.verify(open(userId, sealed), answer.code(), now(), null);
    if (step.isEmpty()) {
      return null;
    }
    Instant now = now();
    mfa.save(userId, sealed, step.getAsLong(), now);
    store.deleteSetupSecret(userId);
    List<String> codes = RecoveryCodes.generate();
    mfa.replaceRecoveryCodes(
        userId, codes.stream().map(c -> RecoveryCodes.hash(userId, c)).toList(), now);
    log.info("2단계 인증 등록 userId={}", userId);
    return codes;
  }

  /** 등록된 비밀로 TOTP 코드를, 또는 복구 코드를 확인한다. 맞으면 그 단계·복구 코드를 쓴 것으로 표시한다 */
  private boolean check(MfaEnrollment e, Answer answer) {
    if (answer.recoveryCode() != null) {
      return mfa.useRecoveryCode(
          e.userId(), RecoveryCodes.hash(e.userId(), answer.recoveryCode()), now());
    }
    OptionalLong step =
        totp.verify(open(e.userId(), e.secret()), answer.code(), now(), e.lastUsedStep());
    return step.isPresent() && mfa.useStep(e.userId(), step.getAsLong());
  }

  private String open(long userId, SealedMfaSecret sealed) {
    try {
      return cipher.open(sealed, MfaEnrollment.aad(userId));
    } catch (MfaSecretUnavailableException e) {
      logUnavailable(userId, e);
      throw new BusinessException(ErrorCode.SECRET_KEY_MISSING);
    }
  }

  private static void logUnavailable(long userId, MfaSecretUnavailableException e) {
    log.warn(
        "2단계 인증 비밀을 열 수 없어요 (마스터 키가 없거나 바뀜). 복구 코드로 들어와 다시 등록하세요. userId={} cause={}",
        userId,
        e.getMessage());
  }

  /** 틀림을 남기고 횟수를 센다. MAX_FAILURES에 닿으면 잠그고 대기 토큰을 지운다 */
  private BusinessException failed(long userId, ClientInfo by, String pendingHash) {
    Instant now = now();
    events.record(AuthEvent.of(userId, AuthEvent.Type.MFA_FAILED, null, by, now));
    long n = store.addFailure(userId, MfaPolicy.LOCK_DURATION);
    if (n < MfaPolicy.MAX_FAILURES) {
      return new BusinessException(
          ErrorCode.MFA_CODE_INVALID, Map.of("remainingAttempts", MfaPolicy.MAX_FAILURES - n));
    }
    store.lock(userId, MfaPolicy.LOCK_DURATION);
    store.clearFailures(userId);
    if (pendingHash != null) {
      store.deletePending(pendingHash);
    }
    events.record(AuthEvent.of(userId, AuthEvent.Type.MFA_LOCKED, null, by, now));
    log.warn("2단계 인증 잠금 userId={} ({}번 틀림, {}분)", userId, n, MfaPolicy.LOCK_DURATION.toMinutes());
    return locked(MfaPolicy.LOCK_DURATION);
  }

  private void ensureNotLocked(long userId) {
    Optional<Duration> left = store.lockRemaining(userId);
    if (left.isPresent()) {
      throw locked(left.get());
    }
  }

  private static BusinessException locked(Duration left) {
    long seconds = Math.max(1, (left.toMillis() + 999) / 1000);
    return new BusinessException(ErrorCode.MFA_LOCKED, Map.of("retryAfterSeconds", seconds));
  }

  private boolean passwordMatches(long userId, String raw) {
    Optional<LocalCredential> found = credentials.findByUserId(userId);
    if (raw == null || raw.isEmpty() || found.isEmpty()) {
      passwords.matches("wearly-mfa-dummy", dummyHash);
      return false;
    }
    return passwords.matches(raw, found.get().passwordHash());
  }

  private ClientInfo clientOf(String sessionId) {
    return sessions.clientOf(sessionId).orElse(new ClientInfo(ClientKind.ADMIN, null, null));
  }

  private Instant now() {
    return clock.instant();
  }

  private static ClientInfo info(ClientDto c) {
    ClientDto v = c == null ? ClientDto.UNKNOWN : c;
    return ClientInfo.of(v.clientHeader(), v.userAgent(), v.ip());
  }

  private record Pending(String tokenHash, UserAccountDto user) {}

  /** 확인할 값: TOTP 코드나 복구 코드 중 하나. 형식이 틀리면 400 VALIDATION_FAILED (틀린 횟수에 넣지 않는다) */
  private record Answer(String code, String recoveryCode) {

    static Answer of(String code, String recoveryCode) {
      boolean hasCode = code != null && !code.isBlank();
      boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();
      if (!hasCode && !hasRecovery) {
        throw BusinessException.validation(FieldError.required("code"));
      }
      if (hasCode && hasRecovery) {
        throw BusinessException.validation("recoveryCode", FieldErrorCode.CONFLICT);
      }
      if (hasCode) {
        if (!MfaPolicy.isTotpFormat(code)) {
          throw BusinessException.validation(FieldError.invalidFormat("code", "totp"));
        }
        return new Answer(code.replaceAll("\\s", ""), null);
      }
      if (!RecoveryCodes.isFormat(recoveryCode)) {
        throw BusinessException.validation(FieldError.invalidFormat("recoveryCode"));
      }
      return new Answer(null, recoveryCode);
    }
  }
}
