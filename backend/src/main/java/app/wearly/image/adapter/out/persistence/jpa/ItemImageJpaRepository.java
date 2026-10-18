package app.wearly.image.adapter.out.persistence.jpa;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemImageJpaRepository extends JpaRepository<ItemImageEntity, UUID> {

  /** 남의 이미지는 찾지 못한 것으로 본다 */
  Optional<ItemImageEntity> findByIdAndUserId(UUID id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ItemImageEntity e where e.id = :id and e.userId = :userId")
  Optional<ItemImageEntity> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ItemImageEntity e where e.itemId = :itemId and e.userId = :userId")
  Optional<ItemImageEntity> lockByItemIdAndUserId(
      @Param("itemId") Long itemId, @Param("userId") Long userId);

  List<ItemImageEntity> findByUserIdAndItemIdIn(Long userId, Collection<Long> itemIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ItemImageEntity e set e.status = 'DELETED', e.deletedAt = :now"
          + " where e.id in :ids and e.itemId is null and e.status <> 'DELETED'")
  int markDeletedIfUnattached(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

  @Query("select e.id from ItemImageEntity e where e.id in :ids and e.status = 'DELETED'")
  List<UUID> findDeletedIds(@Param("ids") Collection<UUID> ids);
}
