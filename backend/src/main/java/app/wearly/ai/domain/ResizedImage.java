package app.wearly.ai.domain;

/**
 * 공급자에 보내려고 줄인 사진 (메타데이터 없음, EXIF 방향 적용됨).
 *
 * @param bytes JPEG 바이트
 * @param width 가로
 * @param height 세로
 */
public record ResizedImage(byte[] bytes, int width, int height) {

  public static final String MEDIA_TYPE = "image/jpeg";
}
