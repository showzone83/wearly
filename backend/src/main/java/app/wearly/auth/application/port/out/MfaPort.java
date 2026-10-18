package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.MfaEnrollment;
import app.wearly.auth.domain.SealedMfaSecret;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 관리자 TOTP 등록·복구 코드 저장 (tb_user_mfa, tb_user_mfa_recovery_code, JPA) */
public interface MfaPort {

  Optional<MfaEnrollment> find(long userId);

  boolean exists(long userId);

  /** 등록 (이미 있으면 바꾼다). usedStep = 등록 확인에 쓴 단계 */
  void save(long userId, SealedMfaSecret secret, long usedStep, Instant enrolledAt);

  /** 마지막 사용 단계를 step으로 올린다. 이미 step 이상이면(동시 사용·재사용) false */
  boolean useStep(long userId, long step);

  /** 등록과 복구 코드를 지운다. @return 등록이 있었는지 */
  boolean delete(long userId);

  /** 복구 코드를 모두 지우고 새 해시로 바꾼다 */
  void replaceRecoveryCodes(long userId, List<String> codeHashes, Instant createdAt);

  /** 쓰지 않은 그 코드를 쓴 것으로 표시한다. 없거나 이미 썼으면 false */
  boolean useRecoveryCode(long userId, String codeHash, Instant usedAt);

  /** 쓰지 않은 복구 코드 수 */
  int countRecoveryCodes(long userId);
}
