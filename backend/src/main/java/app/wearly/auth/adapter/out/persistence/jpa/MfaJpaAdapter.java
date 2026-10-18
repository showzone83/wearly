package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.auth.application.port.out.MfaPort;
import app.wearly.auth.domain.MfaEnrollment;
import app.wearly.auth.domain.SealedMfaSecret;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** MfaPort 구현 (JPA. 단계·복구 코드 사용은 조건부 UPDATE) */
@Component
class MfaJpaAdapter implements MfaPort {

  private final UserMfaJpaRepository enrollments;
  private final MfaRecoveryCodeJpaRepository codes;

  MfaJpaAdapter(UserMfaJpaRepository enrollments, MfaRecoveryCodeJpaRepository codes) {
    this.enrollments = enrollments;
    this.codes = codes;
  }

  @Override
  public Optional<MfaEnrollment> find(long userId) {
    return enrollments
        .findById(userId)
        .map(
            e ->
                new MfaEnrollment(
                    e.getUserId(),
                    new SealedMfaSecret(e.getSecretCipher(), e.getSecretNonce(), e.getKeyVersion()),
                    e.getLastUsedStep(),
                    e.getEnrolledAt()));
  }

  @Override
  public boolean exists(long userId) {
    return enrollments.existsById(userId);
  }

  @Override
  public void save(long userId, SealedMfaSecret secret, long usedStep, Instant enrolledAt) {
    enrollments.saveAndFlush(
        new UserMfaEntity(
            userId,
            secret.ciphertext(),
            secret.nonce(),
            secret.keyVersion(),
            usedStep,
            enrolledAt));
  }

  @Override
  public boolean useStep(long userId, long step) {
    return enrollments.useStep(userId, step) == 1;
  }

  @Override
  public boolean delete(long userId) {
    codes.deleteByUser(userId);
    boolean existed = enrollments.existsById(userId);
    if (existed) {
      enrollments.deleteById(userId);
      enrollments.flush();
    }
    return existed;
  }

  @Override
  public void replaceRecoveryCodes(long userId, List<String> codeHashes, Instant createdAt) {
    codes.deleteByUser(userId);
    codes.saveAllAndFlush(
        codeHashes.stream().map(h -> new MfaRecoveryCodeEntity(userId, h, createdAt)).toList());
  }

  @Override
  public boolean useRecoveryCode(long userId, String codeHash, Instant usedAt) {
    return codes.use(userId, codeHash, usedAt) == 1;
  }

  @Override
  public int countRecoveryCodes(long userId) {
    return codes.countUnused(userId);
  }
}
