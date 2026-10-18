package app.wearly.image.application.port.out;

import app.wearly.image.domain.InvalidImageException;
import app.wearly.image.domain.ProcessedImage;

/**
 * 사진 검증·다시 인코딩: 실제 바이트로 형식 판별(JPEG·PNG), 픽셀 수 상한, 디코딩, EXIF 방향 적용, 메타데이터 없이 JPEG로 다시 인코딩(original 긴
 * 변 2048, thumb 400).
 */
public interface ImageProcessorPort {

  /**
   * @throws InvalidImageException 사진으로 쓸 수 없을 때 (사유는 reason)
   */
  ProcessedImage process(byte[] bytes);
}
