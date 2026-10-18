package app.wearly.image.domain;

/**
 * 사진으로 쓸 수 없음 (형식이 JPEG·PNG가 아님, 픽셀 수 초과, 디코딩 실패). 응답에는 사유 코드(reason)만 IMAGE_INVALID의 params로 나가고,
 * 메시지는 로그용이다.
 */
public class InvalidImageException extends RuntimeException {

  /** IMAGE_INVALID params.reason 값 */
  public enum Reason {
    /** JPEG·PNG가 아님 (실제 바이트로 판별) */
    FORMAT,
    /** 업로드 파일 크기 초과 */
    TOO_LARGE,
    /** 픽셀 수(해상도) 상한 초과 */
    PIXELS,
    /** 디코딩 실패 (깨진 파일 등) */
    DECODE
  }

  private final Reason reason;

  public InvalidImageException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public InvalidImageException(Reason reason, Throwable cause) {
    super(reason.name(), cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
