package app.wearly.auth.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredentialEntity, Long> {

  Optional<LocalCredentialEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<LocalCredentialEntity> findByUserId(Long userId);
}
