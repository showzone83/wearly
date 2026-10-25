package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.auth.application.port.in.PasswordUseCase;
import app.wearly.auth.application.port.in.dto.PasswordStatusDto;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordHasher;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.PasswordChangePolicy;
import app.wearly.auth.domain.PasswordPolicy;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 변경과 변경 주기 (28 문서). 현재 비밀번호 확인은 AccountUseCase(더미 비교 포함)를 쓴다 */
@Service
public class PasswordService implements PasswordUseCase {

  private static final ClientInfo UNKNOWN_CLIENT = new ClientInfo(null, null, null);

  private final CredentialPort credentials;
  private final AccountUseCase accounts;
  private final PasswordHasher passwords;
  private final SessionService sessions;
  private final AuthEventPort events;
  private final PasswordChangePolicy policy;
  private final Clock clock;

  public PasswordService(
      CredentialPort credentials,
      AccountUseCase accounts,
      PasswordHasher passwords,
      SessionService sessions,
      AuthEventPort events,
      PasswordChangePolicy policy,
      Clock clock) {
    this.credentials = credentials;
    this.accounts = accounts;
    this.passwords = passwords;
    this.sessions = sessions;
    this.events = events;
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public PasswordStatusDto status(long userId) {
    return statusOf(credentials.findByUserId(userId), clock.instant());
  }

  @Override
  @Transactional
  public int change(long userId, String keepSessionId, String currentPassword, String newPassword) {
    List<FieldError> errors = new ArrayList<>();
    if (currentPassword == null || currentPassword.isEmpty()) {
      errors.add(FieldError.required("currentPassword"));
    }
    if (newPassword == null || newPassword.isEmpty()) {
      errors.add(FieldError.required("newPassword"));
    } else if (!PasswordPolicy.isValid(newPassword)) {
      errors.add(FieldError.invalidFormat("newPassword"));
    } else if (newPassword.equals(currentPassword)) {
      // 같은 비밀번호로 바꾸면 주기만 다시 시작된다 (28 문서 R6)
      errors.add(FieldError.notAllowed("newPassword"));
    }
    BusinessException.throwIfAny(errors);
    if (!accounts.passwordMatches(userId, currentPassword)) {
      throw new BusinessException(ErrorCode.REAUTH_FAILED);
    }
    LocalCredential credential =
        credentials.findByUserId(userId).orElseThrow(BusinessException::notFound);
    Instant now = clock.instant();
    credentials.changePassword(credential.id(), passwords.encode(newPassword), now);
    record(userId, AuthEvent.Type.PASSWORD_CHANGED, keepSessionId, now);
    return sessions.endOtherSessions(userId, keepSessionId);
  }

  @Override
  @Transactional
  public PasswordStatusDto postpone(long userId, String sessionId) {
    LocalCredential credential =
        credentials
            .findByUserId(userId)
            .filter(c -> policy.enabled())
            .orElseThrow(BusinessException::notFound);
    Instant now = clock.instant();
    credentials.postpone(credential.id(), now);
    record(userId, AuthEvent.Type.PASSWORD_POSTPONED, sessionId, now);
    return statusOf(Optional.of(credential.postponedAt(now)), now);
  }

  private PasswordStatusDto statusOf(Optional<LocalCredential> found, Instant now) {
    long days = policy.intervalDays();
    if (found.isEmpty()) {
      return new PasswordStatusDto(null, null, false, days);
    }
    LocalCredential c = found.get();
    return new PasswordStatusDto(
        c.passwordChangedAt(),
        policy.dueAt(c.passwordChangedAt(), c.passwordPostponedAt()),
        policy.isDue(c.passwordChangedAt(), c.passwordPostponedAt(), now),
        days);
  }

  private void record(long userId, AuthEvent.Type type, String sessionId, Instant at) {
    ClientInfo by = sessions.clientOf(sessionId).orElse(UNKNOWN_CLIENT);
    events.record(AuthEvent.of(userId, type, sessionId, by, at));
  }
}
