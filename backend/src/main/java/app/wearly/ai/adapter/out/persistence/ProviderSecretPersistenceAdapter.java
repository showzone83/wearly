package app.wearly.ai.adapter.out.persistence;

import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.ai.domain.SealedSecret;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** ProviderSecretPort 구현 (JPA) */
@Component
class ProviderSecretPersistenceAdapter implements ProviderSecretPort {

    private final ProviderSecretRepository secrets;

    ProviderSecretPersistenceAdapter(ProviderSecretRepository secrets) {
        this.secrets = secrets;
    }

    @Override
    public Optional<ProviderSecret> find(String providerId) {
        return secrets.findById(providerId).map(ProviderSecretPersistenceAdapter::toDomain);
    }

    @Override
    public ProviderSecret save(ProviderSecret s) {
        ProviderSecretEntity e = secrets.findById(s.providerId()).orElseGet(() -> new ProviderSecretEntity(s.providerId()));
        e.apply(s.sealed().ciphertext(), s.sealed().nonce(), (short) s.sealed().keyVersion(), s.last4(), s.updatedBy());
        // 내용이 같아도(같은 키를 다시 넣어도 nonce가 달라 항상 바뀐다) updated_at을 올린다
        return toDomain(secrets.saveAndFlush(e));
    }

    @Override
    public boolean delete(String providerId) {
        Optional<ProviderSecretEntity> found = secrets.findById(providerId);
        found.ifPresent(secrets::delete);
        secrets.flush();
        return found.isPresent();
    }

    private static ProviderSecret toDomain(ProviderSecretEntity e) {
        return new ProviderSecret(e.getProviderId(), new SealedSecret(e.getCiphertext(), e.getNonce(), e.getKeyVersion()),
                e.getLast4(), e.getUpdatedBy(), e.getUpdatedAt());
    }
}
