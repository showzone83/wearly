package app.wearly.image.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** tb_item_image JPA 엔티티 (V11). image_id는 서버가 만든 UUID */
@Entity
@Table(name = "tb_item_image")
public class ItemImageEntity {

  @Id
  @Column(name = "image_id")
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "purpose", nullable = false, length = 10, updatable = false)
  private String purpose;

  @Column(name = "status", nullable = false, length = 10)
  private String status;

  @Column(name = "content_type", nullable = false, length = 30)
  private String contentType;

  @Column(name = "declared_size", nullable = false, updatable = false)
  private long declaredSize;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "upload_key", nullable = false, updatable = false)
  private String uploadKey;

  @Column(name = "object_key")
  private String objectKey;

  @Column(name = "thumb_key")
  private String thumbKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "ready_at")
  private Instant readyAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected ItemImageEntity() {}

  ItemImageEntity(
      UUID id,
      Long userId,
      String purpose,
      long declaredSize,
      String uploadKey,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.purpose = purpose;
    this.declaredSize = declaredSize;
    this.uploadKey = uploadKey;
    this.createdAt = createdAt;
  }

  void apply(
      Long itemId,
      String status,
      String contentType,
      Long sizeBytes,
      Integer width,
      Integer height,
      String objectKey,
      String thumbKey,
      Instant readyAt,
      Instant deletedAt) {
    this.itemId = itemId;
    this.status = status;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.width = width;
    this.height = height;
    this.objectKey = objectKey;
    this.thumbKey = thumbKey;
    this.readyAt = readyAt;
    this.deletedAt = deletedAt;
  }

  public UUID getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getItemId() {
    return itemId;
  }

  public String getStatus() {
    return status;
  }

  public String getContentType() {
    return contentType;
  }

  public long getDeclaredSize() {
    return declaredSize;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public Integer getWidth() {
    return width;
  }

  public Integer getHeight() {
    return height;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getThumbKey() {
    return thumbKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReadyAt() {
    return readyAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
