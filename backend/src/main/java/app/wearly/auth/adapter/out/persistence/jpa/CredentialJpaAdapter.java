package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.domain.EmailTakenException;
import app.wearly.auth.domain.LocalCredential;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** CredentialPort 구현 (JPA) */
@Component
class CredentialJpaAdapter implements CredentialPort {

  private final LocalCredentialJpaRepository credentials;

  CredentialJpaAdapter(LocalCredentialJpaRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  public Optional<LocalCredential> findByEmail(String email) {
    return credentials.findByEmail(email).map(CredentialJpaAdapter::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return credentials.existsByEmail(email);
  }

  @Override
  public Optional<LocalCredential> findByUserId(long userId) {
    return credentials.findByUserId(userId).map(CredentialJpaAdapter::toDomain);
  }

  @Override
  public void create(LocalCredential c) {
    try {
      credentials.saveAndFlush(new LocalCredentialEntity(c.userId(), c.email(), c.passwordHash()));
    } catch (DataIntegrityViolationException e) {
      // 동시에 같은 이메일로 가입한 경우 (uq_user_local_credential_email). API 오류로는 서비스가 바꾼다 (L1)
      throw new EmailTakenException(c.email());
    }
  }

  @Override
  public void markLogin(long credentialId, Instant at) {
    credentials.findById(credentialId).ifPresent(c -> c.markLogin(at));
    credentials.flush();
  }

  @Override
  public void changePassword(long credentialId, String passwordHash) {
    credentials
        .findById(credentialId)
        .orElseThrow(() -> new IllegalStateException("자격 증명이 없어요: " + credentialId))
        .changePassword(passwordHash);
    credentials.flush();
  }

  private static LocalCredential toDomain(LocalCredentialEntity e) {
    return new LocalCredential(e.getId(), e.getUserId(), e.getEmail(), e.getPasswordHash());
  }
}
