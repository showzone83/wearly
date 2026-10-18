package app.wearly.image.adapter.out.persistence.jpa;

import app.wearly.image.application.port.out.ItemImagePort;
import app.wearly.image.domain.ImageRules;
import app.wearly.image.domain.ImageStatus;
import app.wearly.image.domain.ItemImage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ItemImagePort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class ItemImageJpaAdapter implements ItemImagePort {

  private final ItemImageJpaRepository images;

  ItemImageJpaAdapter(ItemImageJpaRepository images) {
    this.images = images;
  }

  @Override
  public Optional<ItemImage> find(long userId, UUID imageId) {
    return images.findByIdAndUserId(imageId, userId).map(ItemImageJpaAdapter::toDomain);
  }

  @Override
  public Optional<ItemImage> findForUpdate(long userId, UUID imageId) {
    return images.lockByIdAndUserId(imageId, userId).map(ItemImageJpaAdapter::toDomain);
  }

  @Override
  public Optional<ItemImage> findByItemForUpdate(long userId, long itemId) {
    return images.lockByItemIdAndUserId(itemId, userId).map(ItemImageJpaAdapter::toDomain);
  }

  @Override
  public List<ItemImage> findByItems(long userId, Collection<Long> itemIds) {
    return images.findByUserIdAndItemIdIn(userId, itemIds).stream()
        .map(ItemImageJpaAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public ItemImage save(ItemImage image) {
    ItemImageEntity e =
        images
            .findById(image.id())
            .orElseGet(
                () ->
                    new ItemImageEntity(
                        image.id(),
                        image.userId(),
                        ImageRules.PURPOSE_ITEM,
                        image.declaredSize(),
                        image.uploadKey(),
                        image.createdAt()));
    e.apply(
        image.itemId(),
        image.status().name(),
        image.contentType(),
        image.sizeBytes(),
        image.width(),
        image.height(),
        image.objectKey(),
        image.thumbKey(),
        image.readyAt(),
        image.deletedAt());
    return toDomain(images.saveAndFlush(e));
  }

  @Override
  @Transactional
  public List<UUID> claimForCleanup(Collection<UUID> imageIds, Instant now) {
    if (imageIds.isEmpty()) {
      return List.of();
    }
    images.markDeletedIfUnattached(imageIds, now);
    return images.findDeletedIds(imageIds);
  }

  @Override
  @Transactional
  public void deleteAll(Collection<UUID> imageIds) {
    if (!imageIds.isEmpty()) {
      images.deleteAllByIdInBatch(imageIds);
    }
  }

  static ItemImage toDomain(ItemImageEntity e) {
    return new ItemImage(
        e.getId(),
        e.getUserId(),
        e.getItemId(),
        ImageStatus.valueOf(e.getStatus()),
        e.getContentType(),
        e.getDeclaredSize(),
        e.getSizeBytes(),
        e.getWidth(),
        e.getHeight(),
        e.getObjectKey(),
        e.getThumbKey(),
        e.getCreatedAt(),
        e.getReadyAt(),
        e.getDeletedAt());
  }
}
