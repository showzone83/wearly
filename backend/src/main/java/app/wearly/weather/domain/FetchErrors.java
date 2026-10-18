package app.wearly.weather.domain;

/** 공급자 호출 오류 코드 (tb_weather_call_log.error_code, 17 문서 4장) */
public final class FetchErrors {

  /** 응답 시간 초과 */
  public static final String TIMEOUT = "TIMEOUT";

  /** 연결 실패 등 HTTP 응답을 받지 못함 */
  public static final String CLIENT = "CLIENT";

  /** 401·403: 키가 틀렸거나 막힘. 공급자를 바로 차단한다 */
  public static final String AUTH = "AUTH";

  /** 공급자 쪽 월 호출 한도 초과 */
  public static final String QUOTA = "QUOTA";

  public static final String HTTP_429 = "HTTP_429";
  public static final String HTTP_4XX = "HTTP_4XX";
  public static final String HTTP_5XX = "HTTP_5XX";

  /** 응답을 읽지 못함 (모양이 다름) */
  public static final String PARSE = "PARSE";

  private FetchErrors() {}

  /** HTTP 상태 → 오류 코드 (401·403은 AUTH) */
  public static String ofStatus(int status) {
    if (status == 401 || status == 403) {
      return AUTH;
    }
    if (status == 429) {
      return HTTP_429;
    }
    return status >= 500 ? HTTP_5XX : HTTP_4XX;
  }
}
