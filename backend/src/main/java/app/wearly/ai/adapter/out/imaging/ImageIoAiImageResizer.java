package app.wearly.ai.adapter.out.imaging;

import app.wearly.ai.application.port.out.AiImageResizePort;
import app.wearly.ai.domain.ImageTypes;
import app.wearly.ai.domain.ResizedImage;
import app.wearly.common.config.WearlyProperties;
import app.wearly.common.imaging.ImageCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 분석 사진을 공급자에 보내기 전에 줄인다 (ImageCodec: image 컨텍스트의 저장용 처리와 같은 도구).
 *
 * <ol>
 *   <li>실제 바이트가 JPEG·PNG가 아니면 그대로 보낸다 (webp·heic는 ImageIO로 읽지 못한다)
 *   <li>헤더의 긴 변이 image-max-side 이하면 그대로 보낸다 (다시 인코딩하지 않는다)
 *   <li>헤더의 가로×세로가 wearly.storage.max-pixels를 넘으면 디코딩하지 않고 그대로 보낸다
 *   <li>디코딩 → 긴 변 image-max-side로 줄임 → EXIF 방향 적용 → 메타데이터 없는 JPEG(image-quality)
 * </ol>
 *
 * 읽기·쓰기에 실패하면 경고 로그를 남기고 그대로 보낸다 (공급자가 판단한다). 동시에 줄이는 사진 수는 wearly.storage.processing-concurrency로
 * 제한한다 (저장용 처리와 따로 센다).
 */
@Component
class ImageIoAiImageResizer implements AiImageResizePort {

  private static final Logger log = LoggerFactory.getLogger(ImageIoAiImageResizer.class);

  private final int maxSide;
  private final float quality;
  private final long maxPixels;
  private final Semaphore permits;

  @Autowired
  ImageIoAiImageResizer(WearlyProperties properties) {
    this(
        properties.ai().imageMaxSide(),
        properties.ai().imageQuality().floatValue(),
        properties.storage().maxPixels(),
        properties.storage().processingConcurrency());
  }

  ImageIoAiImageResizer(int maxSide, float quality, long maxPixels, int concurrency) {
    this.maxSide = maxSide;
    this.quality = quality;
    this.maxPixels = maxPixels;
    this.permits = new Semaphore(concurrency, true);
  }

  @Override
  public Optional<ResizedImage> shrink(byte[] bytes) {
    String format = format(bytes);
    if (format == null) {
      return Optional.empty();
    }
    try {
      ImageCodec.Size size = ImageCodec.size(bytes, format);
      if (size.longSide() <= maxSide) {
        return Optional.empty();
      }
      if (size.pixels() > maxPixels) {
        log.warn("AI 분석 사진 줄이기 건너뜀: 픽셀 수 상한 초과 {}x{}", size.width(), size.height());
        return Optional.empty();
      }
      permits.acquireUninterruptibly();
      try {
        BufferedImage decoded = ImageCodec.decode(bytes, format, maxSide, maxPixels);
        BufferedImage fitted = ImageCodec.fit(decoded, maxSide);
        decoded = null;
        BufferedImage upright = ImageCodec.orient(fitted, ImageCodec.orientation(bytes));
        return Optional.of(
            new ResizedImage(
                ImageCodec.encodeJpeg(upright, quality), upright.getWidth(), upright.getHeight()));
      } finally {
        permits.release();
      }
    } catch (IOException | RuntimeException e) {
      warn(e);
      return Optional.empty();
    }
  }

  private static String format(byte[] bytes) {
    String type = ImageTypes.sniff(bytes).orElse(null);
    if ("image/jpeg".equals(type)) {
      return ImageCodec.JPEG;
    }
    if ("image/png".equals(type)) {
      return ImageCodec.PNG;
    }
    return null;
  }

  private static void warn(Exception e) {
    log.warn("AI 분석 사진 줄이기 실패, 원본 그대로 보냄: {} {}", e.getClass().getSimpleName(), e.getMessage());
  }
}
