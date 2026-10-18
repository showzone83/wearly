package app.wearly.auth.adapter.out.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSsoJpaRepository extends JpaRepository<UserSsoEntity, Long> {

  Optional<UserSsoEntity> findByIssuerAndSubject(String issuer, String subject);
}
