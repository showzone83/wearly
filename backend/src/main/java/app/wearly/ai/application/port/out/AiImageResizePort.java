package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.ResizedImage;
import java.util.Optional;

/**
 * 분석 사진 줄이기: 공급자에 보내기 전에 긴 변을 wearly.ai.image-max-side(기본 1024) 이하 JPEG(image-quality, 기본 0.8)로 다시
 * 인코딩한다. 비어 있으면 원본 그대로 보낸다 (이미 작음, JPEG·PNG가 아님, 읽지 못함, 픽셀 수 상한 초과).
 */
public interface AiImageResizePort {

  Optional<ResizedImage> shrink(byte[] bytes);
}
