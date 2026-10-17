package app.wearly.auth.domain;

/**
 * 로그인·갱신·로그아웃 요청을 보낸 기기 정보 (16 문서 3장 세션 정보). User-Agent와 IP는 화면 표시용이라 길이를 자른다.
 *
 * @param userAgent 없으면 null
 * @param ip {@code X-Forwarded-For} 첫 값 또는 원격 주소. 없으면 null
 */
public record ClientInfo(ClientKind client, String userAgent, String ip) {

  public static final int USER_AGENT_MAX = 300;
  public static final int IP_MAX = 45;

  /** 웹 어댑터가 넘긴 값 그대로를 정리한다 */
  public static ClientInfo of(String clientHeader, String userAgent, String ip) {
    String ua = clip(userAgent, USER_AGENT_MAX);
    return new ClientInfo(ClientKind.resolve(clientHeader, ua), ua, clip(ip, IP_MAX));
  }

  private static String clip(String s, int max) {
    if (s == null || s.isBlank()) {
      return null;
    }
    String v = s.strip();
    return v.length() > max ? v.substring(0, max) : v;
  }
}
