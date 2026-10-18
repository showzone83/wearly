package app.wearly.image.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.image.application.port.in.ItemImageUseCase;
import app.wearly.image.application.port.in.dto.ItemImageRefDto;
import app.wearly.image.application.port.out.AfterCommitPort;
import app.wearly.image.application.port.out.ImageStoragePort;
import app.wearly.image.application.port.out.ItemImagePort;
import app.wearly.image.domain.ImageRules;
import app.wearly.image.domain.ItemImage;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템 대표 사진 붙이기·빼기·조회 (18 문서 3장). wardrobe 유스케이스의 트랜잭션에 함께 들어가고, 버린 사진의 객체는 커밋된 뒤에 지운다 (롤백되면 그대로
 * 둔다). 객체 삭제가 실패해도 행이 DELETED로 남아 정리 작업이 다시 지운다.
 */
@Service
public class ItemImageService implements ItemImageUseCase {

  private static final Logger log = LoggerFactory.getLogger(ItemImageService.class);

  private final ItemImagePort images;
  private final ImageStoragePort storage;
  private final AfterCommitPort afterCommit;
  private final ImageUrls urls;
  private final Clock clock;

  public ItemImageService(
      ItemImagePort images,
      ImageStoragePort storage,
      AfterCommitPort afterCommit,
      ImageUrls urls,
      Clock clock) {
    this.images = images;
    this.storage = storage;
    this.afterCommit = afterCommit;
    this.urls = urls;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ItemImageRefDto attach(long userId, long itemId, String rawId) {
    UUID id = ImageRules.parseId(rawId).orElseThrow(BusinessException::notFound);
    ItemImage image = images.findForUpdate(userId, id).orElseThrow(BusinessException::notFound);
    if (image.isDeleted()) {
      throw BusinessException.notFound();
    }
    if (!image.isReady()) {
      throw new BusinessException(ErrorCode.IMAGE_NOT_UPLOADED);
    }
    if (image.itemId() != null) {
      if (image.itemId() == itemId) {
        return urls.ref(image);
      }
      throw new BusinessException(ErrorCode.IMAGE_IN_USE);
    }
    // 예전 사진을 먼저 떼야 부분 유니크 인덱스(아이템당 하나)에 걸리지 않는다
    discardCurrent(userId, itemId);
    image.attachTo(itemId);
    return urls.ref(images.save(image));
  }

  @Override
  @Transactional
  public void detach(long userId, long itemId) {
    discardCurrent(userId, itemId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ItemImageRefDto> find(long userId, long itemId) {
    return Optional.ofNullable(findAll(userId, List.of(itemId)).get(itemId));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, ItemImageRefDto> findAll(long userId, Collection<Long> itemIds) {
    Map<Long, ItemImageRefDto> out = new LinkedHashMap<>();
    if (itemIds.isEmpty()) {
      return out;
    }
    for (ItemImage image : images.findByItems(userId, itemIds)) {
      if (image.isReady() && image.itemId() != null) {
        out.put(image.itemId(), urls.ref(image));
      }
    }
    return out;
  }

  private void discardCurrent(long userId, long itemId) {
    Optional<ItemImage> current = images.findByItemForUpdate(userId, itemId);
    if (current.isEmpty()) {
      return;
    }
    ItemImage old = current.get();
    old.markDeleted(clock.instant());
    images.save(old);
    List<String> keys = old.allKeys();
    afterCommit.afterCommit(
        () -> {
          try {
            storage.delete(keys);
          } catch (RuntimeException e) {
            log.warn("버린 사진 객체 삭제 실패 (정리 작업이 다시 지운다): {} — {}", keys, e.toString());
          }
        });
  }
}
