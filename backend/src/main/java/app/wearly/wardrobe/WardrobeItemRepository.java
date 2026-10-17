package app.wearly.wardrobe;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, Long> {

    /** 남의 아이템은 찾지 못한 것으로 본다 (404) */
    Optional<WardrobeItem> findByIdAndUserId(Long id, Long userId);
}
