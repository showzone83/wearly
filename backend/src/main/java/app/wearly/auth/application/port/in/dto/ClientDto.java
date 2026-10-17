package app.wearly.auth.application.port.in.dto;

/**
 * 인증 요청을 보낸 기기 (웹 어댑터가 헤더에서 읽은 값 그대로). clientHeader = {@code X-Wearly-Client}, ip = {@code
 * X-Forwarded-For} 첫 값 또는 원격 주소. 모두 없을 수 있다
 */
public record ClientDto(String clientHeader, String userAgent, String ip) {

  public static final ClientDto UNKNOWN = new ClientDto(null, null, null);
}
