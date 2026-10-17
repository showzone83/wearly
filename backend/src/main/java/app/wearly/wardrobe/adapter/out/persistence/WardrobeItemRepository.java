package app.wearly.wardrobe.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardrobeItemRepository extends JpaRepository<WardrobeItemEntity, Long> {

  /** 남의 아이템은 찾지 못한 것으로 본다 (404) */
  Optional<WardrobeItemEntity> findByIdAndUserId(Long id, Long userId);
}
