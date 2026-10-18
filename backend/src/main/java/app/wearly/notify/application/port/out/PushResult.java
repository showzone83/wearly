package app.wearly.notify.application.port.out;

/**
 * 푸시 한 건의 결과 (보낸 순서와 같다).
 *
 * @param error 실패했을 때 공급자 오류 코드(예: DeviceNotRegistered, MessageRateExceeded, HTTP_503, NETWORK).
 *     성공이면 null
 */
public record PushResult(Status status, String error) {

  public enum Status {
    /** 공급자가 받았다 (기기 도착을 뜻하지는 않는다) */
    OK,
    /** 기기에서 앱이 지워졌거나 토큰이 무효다 → 토큰을 지운다 */
    DEVICE_NOT_REGISTERED,
    /** 그 밖의 실패 */
    FAILED
  }

  public static PushResult ok() {
    return new PushResult(Status.OK, null);
  }

  public static PushResult failed(String error) {
    return new PushResult(Status.FAILED, error);
  }
}
