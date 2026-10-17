package app.wearly.user.adapter.out.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, Long> {

  Optional<UserProfileEntity> findByUserId(Long userId);
}
