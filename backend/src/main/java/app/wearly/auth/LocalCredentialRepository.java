package app.wearly.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<LocalCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}
