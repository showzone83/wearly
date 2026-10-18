package app.wearly.image.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 옷 사진 한 장 (tb_item_image, 18 문서 2장). PENDING(업로드 URL 발급) → READY(complete: 검증·다시 인코딩) → 아이템에 붙음 →
 * DELETED(교체·아이템 삭제·처리 실패). 한 아이템에 READY 사진 하나.
 */
public class ItemImage {

  private final UUID id;
  private final long userId;
  private Long itemId;
  private ImageStatus status;
  private String contentType;
  private final long declaredSize;
  private Long sizeBytes;
  private Integer width;
  private Integer height;
  private String objectKey;
  private String thumbKey;
  private final Instant createdAt;
  private Instant readyAt;
  private Instant deletedAt;

  public ItemImage(
      UUID id,
      long userId,
      Long itemId,
      ImageStatus status,
      String contentType,
      long declaredSize,
      Long sizeBytes,
      Integer width,
      Integer height,
      String objectKey,
      String thumbKey,
      Instant createdAt,
      Instant readyAt,
      Instant deletedAt) {
    this.id = id;
    this.userId = userId;
    this.itemId = itemId;
    this.status = status;
    this.contentType = contentType;
    this.declaredSize = declaredSize;
    this.sizeBytes = sizeBytes;
    this.width = width;
    this.height = height;
    this.objectKey = objectKey;
    this.thumbKey = thumbKey;
    this.createdAt = createdAt;
    this.readyAt = readyAt;
    this.deletedAt = deletedAt;
  }

  /** 업로드 URL을 발급할 때 만든다 */
  public static ItemImage pending(
      UUID id, long userId, String contentType, long declaredSize, Instant now) {
    return new ItemImage(
        id,
        userId,
        null,
        ImageStatus.PENDING,
        contentType,
        declaredSize,
        null,
        null,
        null,
        null,
        null,
        now,
        null,
        null);
  }

  /** original·thumb를 저장했다 */
  public void markReady(long originalBytes, int width, int height, Instant now) {
    this.status = ImageStatus.READY;
    this.contentType = ImageFormats.JPEG;
    this.sizeBytes = originalBytes;
    this.width = width;
    this.height = height;
    this.objectKey = ImageKeys.original(userId, id);
    this.thumbKey = ImageKeys.thumb(userId, id);
    this.readyAt = now;
  }

  public void attachTo(long itemId) {
    if (status != ImageStatus.READY) {
      throw new IllegalStateException("READY가 아닌 이미지는 아이템에 붙일 수 없어요: " + id);
    }
    this.itemId = itemId;
  }

  /** 버린다. 아이템에서 떨어지고 객체는 서비스가 지운다 */
  public void markDeleted(Instant now) {
    this.status = ImageStatus.DELETED;
    this.itemId = null;
    this.deletedAt = now;
  }

  public boolean isReady() {
    return status == ImageStatus.READY;
  }

  public boolean isDeleted() {
    return status == ImageStatus.DELETED;
  }

  public String uploadKey() {
    return ImageKeys.upload(userId, id);
  }

  /** 이 이미지가 쓰는(쓸 수 있는) 객체 키 전부: 업로드 원본, original, thumb */
  public List<String> allKeys() {
    List<String> keys = new ArrayList<>(3);
    keys.add(uploadKey());
    keys.add(objectKey != null ? objectKey : ImageKeys.original(userId, id));
    keys.add(thumbKey != null ? thumbKey : ImageKeys.thumb(userId, id));
    return keys;
  }

  public UUID id() {
    return id;
  }

  public long userId() {
    return userId;
  }

  public Long itemId() {
    return itemId;
  }

  public ImageStatus status() {
    return status;
  }

  public String contentType() {
    return contentType;
  }

  public long declaredSize() {
    return declaredSize;
  }

  public Long sizeBytes() {
    return sizeBytes;
  }

  public Integer width() {
    return width;
  }

  public Integer height() {
    return height;
  }

  public String objectKey() {
    return objectKey;
  }

  public String thumbKey() {
    return thumbKey;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant readyAt() {
    return readyAt;
  }

  public Instant deletedAt() {
    return deletedAt;
  }
}
