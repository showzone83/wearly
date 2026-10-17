package app.wearly.ai.adapter.out.persistence;

import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** AiProviderPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class AiProviderPersistenceAdapter implements AiProviderPort {

    private final AiProviderRepository providers;

    AiProviderPersistenceAdapter(AiProviderRepository providers) {
        this.providers = providers;
    }

    @Override
    public List<AiProviderSetting> findAllOrdered() {
        return providers.findAllByOrderByPriorityAscProviderIdAsc().stream().map(AiProviderPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<AiProviderSetting> findById(String providerId) {
        return providers.findById(providerId).map(AiProviderPersistenceAdapter::toDomain);
    }

    @Override
    public boolean exists(String providerId) {
        return providers.existsById(providerId);
    }

    @Override
    public int maxPriority() {
        return providers.maxPriority();
    }

    @Override
    public AiProviderSetting create(AiProviderSetting s) {
        AiProviderEntity e = new AiProviderEntity(s.providerId());
        apply(e, s);
        return toDomain(providers.saveAndFlush(e));
    }

    @Override
    public AiProviderSetting update(AiProviderSetting s) {
        AiProviderEntity e = providers.findById(s.providerId())
                .orElseThrow(() -> new IllegalStateException("공급자가 없어요: " + s.providerId()));
        apply(e, s);
        return toDomain(providers.saveAndFlush(e));
    }

    @Override
    public void delete(String providerId) {
        providers.findById(providerId).ifPresent(providers::delete);
        providers.flush();
    }

    private static void apply(AiProviderEntity e, AiProviderSetting s) {
        e.update(s.type().name(), s.displayName(), s.model(), s.apiKeyEnv(), s.enabled(), s.priority(), s.options());
    }

    static AiProviderSetting toDomain(AiProviderEntity e) {
        return new AiProviderSetting(e.getProviderId(), AiProviderType.valueOf(e.getType()), e.getDisplayName(),
                e.getModel(), e.getApiKeyEnv(), e.isEnabled(), e.getPriority(), e.getOptions(), e.getUpdatedAt());
    }
}
