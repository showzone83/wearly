package app.wearly.image.adapter.out.imaging;

import app.wearly.common.config.WearlyProperties;
import app.wearly.common.imaging.ImageCodec;
import app.wearly.image.application.port.out.ImageProcessorPort;
import app.wearly.image.domain.ImageFormats;
import app.wearly.image.domain.InvalidImageException;
import app.wearly.image.domain.ProcessedImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 자바 ImageIO로 사진을 검증하고 다시 인코딩한다 (18 문서 3장). 읽기·줄이기·쓰기는 공용 도구 ImageCodec이 한다.
 *
 * <ol>
 *   <li>실제 바이트로 형식 판별 (JPEG·PNG만)
 *   <li>헤더의 가로·세로로 픽셀 수 상한(wearly.storage.max-pixels) 확인 — 디코딩 전에 막아 메모리 폭주를 피한다
 *   <li>디코딩 (긴 변이 목표의 2배를 넘으면 서브샘플링으로 줄여 읽는다)
 *   <li>EXIF Orientation(metadata-extractor로 읽음) 적용
 *   <li>흰 배경 RGB로 바꿔 긴 변 original-max-side(기본 1600, 품질 0.8)·thumb-max-side(기본 400, 품질 0.8)로 줄이고,
 *       ImageIO JPEG 인코더로 다시 쓴다. 원본의 EXIF(위치 정보 포함)·ICC·XMP 등 메타데이터는 옮기지 않는다
 * </ol>
 *
 * 동시에 처리하는 사진 수를 wearly.storage.processing-concurrency로 제한한다.
 */
@Component
class ImageIoProcessorAdapter implements ImageProcessorPort {

  /** 크기·품질 (wearly.storage.original-max-side·original-quality·thumb-max-side·thumb-quality) */
  record Sizes(int originalMaxSide, float originalQuality, int thumbMaxSide, float thumbQuality) {

    static final Sizes DEFAULT = new Sizes(1600, 0.8f, 400, 0.8f);
  }

  private final long maxPixels;
  private final Semaphore permits;
  private final Sizes sizes;

  @Autowired
  ImageIoProcessorAdapter(WearlyProperties properties) {
    this(
        properties.storage().maxPixels(),
        properties.storage().processingConcurrency(),
        new Sizes(
            properties.storage().originalMaxSide(),
            properties.storage().originalQuality().floatValue(),
            properties.storage().thumbMaxSide(),
            properties.storage().thumbQuality().floatValue()));
  }

  ImageIoProcessorAdapter(long maxPixels, int concurrency) {
    this(maxPixels, concurrency, Sizes.DEFAULT);
  }

  ImageIoProcessorAdapter(long maxPixels, int concurrency, Sizes sizes) {
    this.maxPixels = maxPixels;
    this.permits = new Semaphore(concurrency, true);
    this.sizes = sizes;
  }

  @Override
  public ProcessedImage process(byte[] bytes) {
    String format =
        ImageFormats.sniff(bytes)
            .orElseThrow(() -> new InvalidImageException(InvalidImageException.Reason.FORMAT));
    permits.acquireUninterruptibly();
    try {
      BufferedImage decoded =
          ImageCodec.decode(
              bytes,
              ImageFormats.PNG.equals(format) ? ImageCodec.PNG : ImageCodec.JPEG,
              sizes.originalMaxSide(),
              maxPixels);
      BufferedImage original = ImageCodec.fit(decoded, sizes.originalMaxSide());
      decoded = null;
      original = ImageCodec.orient(original, ImageCodec.orientation(bytes));
      BufferedImage thumb = ImageCodec.fit(original, sizes.thumbMaxSide());
      return new ProcessedImage(
          ImageCodec.encodeJpeg(original, sizes.originalQuality()),
          original.getWidth(),
          original.getHeight(),
          ImageCodec.encodeJpeg(thumb, sizes.thumbQuality()));
    } catch (ImageCodec.TooManyPixelsException e) {
      throw new InvalidImageException(InvalidImageException.Reason.PIXELS);
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidImageException invalid) {
        throw invalid;
      }
      throw new InvalidImageException(InvalidImageException.Reason.DECODE, e);
    } finally {
      permits.release();
    }
  }

  /** EXIF 방향 적용 (테스트용으로 남긴 위임) */
  static BufferedImage orient(BufferedImage src, int orientation) {
    return ImageCodec.orient(src, orientation);
  }
}
