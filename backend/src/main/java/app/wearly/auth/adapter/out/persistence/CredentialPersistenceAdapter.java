package app.wearly.auth.adapter.out.persistence;

import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** CredentialPort 구현 (JPA) */
@Component
class CredentialPersistenceAdapter implements CredentialPort {

    private final LocalCredentialRepository credentials;

    CredentialPersistenceAdapter(LocalCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    public Optional<LocalCredential> findByEmail(String email) {
        return credentials.findByEmail(email).map(CredentialPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return credentials.existsByEmail(email);
    }

    @Override
    public void create(LocalCredential c) {
        try {
            credentials.saveAndFlush(new LocalCredentialEntity(c.userId(), c.email(), c.passwordHash()));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입한 경우 (uq_user_local_credential_email)
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
    }

    @Override
    public void markLogin(long credentialId, Instant at) {
        credentials.findById(credentialId).ifPresent(c -> c.markLogin(at));
    }

    private static LocalCredential toDomain(LocalCredentialEntity e) {
        return new LocalCredential(e.getId(), e.getUserId(), e.getEmail(), e.getPasswordHash());
    }
}
