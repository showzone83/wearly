package app.wearly.weather.domain;

import java.time.Instant;

/**
 * 공급자 HTTP 호출 한 번 (tb_weather_call_log). 오류 문구에는 키가 없다 (어댑터가 지운다).
 *
 * @param callKind FORECAST (현재 날씨와 일별 예보를 한 번에)
 * @param fallback 1순위가 아닌 공급자를 부른 호출인지
 * @param httpStatus 응답을 받지 못했으면 null
 * @param responseBytes 응답을 받지 못했으면 null
 */
public record CallRecord(
    String regionCode,
    String providerCode,
    String callKind,
    boolean fallback,
    Integer httpStatus,
    boolean success,
    String errorCode,
    String errorMessage,
    long durationMs,
    Integer responseBytes,
    Instant calledAt) {

  public static final String FORECAST = "FORECAST";

  public CallRecord asFallback(boolean value) {
    return new CallRecord(
        regionCode,
        providerCode,
        callKind,
        value,
        httpStatus,
        success,
        errorCode,
        errorMessage,
        durationMs,
        responseBytes,
        calledAt);
  }
}
