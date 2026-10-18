package app.wearly.weather.domain;

import java.time.Instant;

/**
 * 공급자 HTTP 호출 한 번 (tb_weather_call_log). 오류 문구에는 키가 없다 (어댑터가 지운다).
 *
 * @param regionCode FORECAST는 REGION 코드, ON_DEMAND·CLIMATE는 격자 키("37.6,127.0"), SEARCH는 null
 * @param callKind FORECAST(실행의 예보) · ON_DEMAND(좌표 요청 때 부른 예보) · SEARCH(장소 검색) · CLIMATE(평년값용 과거 자료)
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

  /** 주기·수동·시작·시험 실행에서 부른 예보 (현재 날씨와 일별 예보를 한 번에) */
  public static final String FORECAST = "FORECAST";

  /** REGION 밖 좌표를 요청 시점에 부른 예보 (실행 없음) */
  public static final String ON_DEMAND = "ON_DEMAND";

  /** 장소 검색 (실행 없음) */
  public static final String SEARCH = "SEARCH";

  /** 평년값 계산용 과거 기상 자료 (실행 없음, 공급자 OPEN_METEO) */
  public static final String CLIMATE = "CLIMATE";

  public CallRecord withKind(String kind) {
    return new CallRecord(
        regionCode,
        providerCode,
        kind,
        fallback,
        httpStatus,
        success,
        errorCode,
        errorMessage,
        durationMs,
        responseBytes,
        calledAt);
  }

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
