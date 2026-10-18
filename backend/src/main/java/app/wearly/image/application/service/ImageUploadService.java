package app.wearly.image.application.service;

import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.image.application.port.in.ImageContentUseCase;
import app.wearly.image.application.port.in.ImageUploadUseCase;
import app.wearly.image.application.port.in.dto.CreateUploadUrlDto;
import app.wearly.image.application.port.in.dto.ImageContentDto;
import app.wearly.image.application.port.in.dto.ImageDto;
import app.wearly.image.application.port.in.dto.UploadUrlDto;
import app.wearly.image.application.port.out.ImageProcessorPort;
import app.wearly.image.application.port.out.ImageStoragePort;
import app.wearly.image.application.port.out.ItemImagePort;
import app.wearly.image.application.port.out.ItemImageQueryPort;
import app.wearly.image.domain.ImageFormats;
import app.wearly.image.domain.ImageRules;
import app.wearly.image.domain.ImageSettings;
import app.wearly.image.domain.InvalidImageException;
import app.wearly.image.domain.ItemImage;
import app.wearly.image.domain.PresignedUrl;
import app.wearly.image.domain.ProcessedImage;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 올리기 (18 문서 3장). 업로드 URL 발급은 한 트랜잭션(한도 잠금 → 집계 → PENDING 행). complete는 저장소 읽기·쓰기가 길어서 트랜잭션 없이
 * 하고 행 저장만 짧게 한다.
 */
@Service
public class ImageUploadService implements ImageUploadUseCase, ImageContentUseCase {

  private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

  private final ItemImagePort images;
  private final ItemImageQueryPort queries;
  private final ImageStoragePort storage;
  private final ImageProcessorPort processor;
  private final ImageUrls urls;
  private final ImageSettings settings;
  private final Clock clock;

  public ImageUploadService(
      ItemImagePort images,
      ItemImageQueryPort queries,
      ImageStoragePort storage,
      ImageProcessorPort processor,
      ImageUrls urls,
      ImageSettings settings,
      Clock clock) {
    this.images = images;
    this.queries = queries;
    this.storage = storage;
    this.processor = processor;
    this.urls = urls;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional
  public UploadUrlDto createUploadUrl(long userId, CreateUploadUrlDto input) {
    BusinessException.throwIfAny(
        ImageRules.validateUpload(
            input.purpose(), input.contentType(), input.size(), settings.maxUploadBytes()));
    queries.lockUploads(userId);
    long used = queries.countCreatedSince(userId, Zones.startOf(Zones.today(clock)));
    if (used >= settings.dailyUploadLimit()) {
      throw new BusinessException(ErrorCode.IMAGE_UPLOAD_LIMIT);
    }
    ItemImage image =
        images.save(
            ItemImage.pending(
                UUID.randomUUID(), userId, input.contentType(), input.size(), clock.instant()));
    PresignedUrl put =
        storage.presignPut(image.uploadKey(), input.contentType(), settings.uploadUrlTtl());
    return new UploadUrlDto(
        image.id(), put.url(), "PUT", Map.copyOf(put.headers()), put.expiresAt());
  }

  @Override
  public ImageDto complete(long userId, String rawId) {
    ItemImage image = findLive(userId, rawId);
    if (image.isReady()) {
      return urls.image(image);
    }
    String uploadKey = image.uploadKey();
    long size = storage.size(uploadKey).orElseThrow(() -> notUploaded());
    if (size > settings.maxUploadBytes()) {
      throw reject(image, InvalidImageException.Reason.TOO_LARGE);
    }
    byte[] bytes = storage.read(uploadKey);
    if (ImageFormats.sniff(bytes).isEmpty()) {
      throw reject(image, InvalidImageException.Reason.FORMAT);
    }
    ProcessedImage processed;
    try {
      processed = processor.process(bytes);
    } catch (InvalidImageException e) {
      throw reject(image, e.reason());
    }
    image.markReady(
        processed.original().length, processed.width(), processed.height(), clock.instant());
    storage.write(image.objectKey(), processed.original(), ImageFormats.JPEG);
    storage.write(image.thumbKey(), processed.thumb(), ImageFormats.JPEG);
    ItemImage saved = images.save(image);
    deleteQuietly(List.of(uploadKey));
    return urls.image(saved);
  }

  @Override
  public ImageContentDto readOriginal(long userId, String rawId) {
    ItemImage image = findLive(userId, rawId);
    if (!image.isReady()) {
      throw notUploaded();
    }
    return new ImageContentDto(storage.read(image.objectKey()), ImageFormats.JPEG);
  }

  /** 내 이미지 (DELETED·형식이 틀린 ID·남의 것은 404) */
  private ItemImage findLive(long userId, String rawId) {
    UUID id = ImageRules.parseId(rawId).orElseThrow(BusinessException::notFound);
    ItemImage image = images.find(userId, id).orElseThrow(BusinessException::notFound);
    if (image.isDeleted()) {
      throw BusinessException.notFound();
    }
    return image;
  }

  /** 처리 실패: 업로드 원본을 지우고 DELETED로 둔 뒤 400 IMAGE_INVALID { reason } */
  private BusinessException reject(ItemImage image, InvalidImageException.Reason reason) {
    deleteQuietly(List.of(image.uploadKey()));
    image.markDeleted(clock.instant());
    images.save(image);
    return new BusinessException(ErrorCode.IMAGE_INVALID, Map.of("reason", reason.name()));
  }

  private void deleteQuietly(List<String> keys) {
    try {
      storage.delete(keys);
    } catch (RuntimeException e) {
      log.warn("사진 객체 삭제 실패 (정리 작업이 다시 지운다): {} — {}", keys, e.toString());
    }
  }

  private static BusinessException notUploaded() {
    return new BusinessException(ErrorCode.IMAGE_NOT_UPLOADED);
  }
}
