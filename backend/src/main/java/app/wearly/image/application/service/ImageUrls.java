package app.wearly.image.application.service;

import app.wearly.image.application.port.in.dto.ImageDto;
import app.wearly.image.application.port.in.dto.ItemImageRefDto;
import app.wearly.image.application.port.out.ImageStoragePort;
import app.wearly.image.domain.ImageSettings;
import app.wearly.image.domain.ItemImage;
import app.wearly.image.domain.PresignedUrl;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** READY 이미지 → GET 서명 URL (original·thumb). 두 URL의 만료 시각 중 이른 것을 expiresAt으로 준다 */
@Component
class ImageUrls {

  private final ImageStoragePort storage;
  private final ImageSettings settings;

  ImageUrls(ImageStoragePort storage, ImageSettings settings) {
    this.storage = storage;
    this.settings = settings;
  }

  ItemImageRefDto ref(ItemImage image) {
    PresignedUrl original = storage.presignGet(image.objectKey(), settings.downloadUrlTtl());
    PresignedUrl thumb = storage.presignGet(image.thumbKey(), settings.downloadUrlTtl());
    return new ItemImageRefDto(image.id(), original.url(), thumb.url(), earlier(original, thumb));
  }

  ImageDto image(ItemImage image) {
    ItemImageRefDto ref = ref(image);
    return new ImageDto(
        ref.imageId(), ref.url(), ref.thumbUrl(), image.width(), image.height(), ref.expiresAt());
  }

  private static Instant earlier(PresignedUrl a, PresignedUrl b) {
    return a.expiresAt().isBefore(b.expiresAt()) ? a.expiresAt() : b.expiresAt();
  }
}
