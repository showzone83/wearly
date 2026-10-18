package app.wearly.image.domain;

/** 사진으로 쓸 수 없음 (형식이 JPEG·PNG가 아님, 크기·픽셀 수 초과, 디코딩 실패). 메시지는 사용자에게 보여 줄 문구 */
public class InvalidImageException extends RuntimeException {

  public InvalidImageException(String message) {
    super(message);
  }

  public InvalidImageException(String message, Throwable cause) {
    super(message, cause);
  }
}
