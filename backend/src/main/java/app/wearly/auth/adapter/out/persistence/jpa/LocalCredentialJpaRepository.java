package app.wearly.auth.adapter.out.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialJpaRepository extends JpaRepository<LocalCredentialEntity, Long> {

  Optional<LocalCredentialEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<LocalCredentialEntity> findByUserId(Long userId);
}
