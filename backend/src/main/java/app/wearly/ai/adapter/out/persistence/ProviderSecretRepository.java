package app.wearly.ai.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSecretRepository extends JpaRepository<ProviderSecretEntity, String> {
}
