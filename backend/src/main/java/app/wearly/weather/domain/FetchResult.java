package app.wearly.weather.domain;

import java.util.List;

/**
 * 공급자 한 곳에 한 지역을 물어본 결과. 성공이면 report가 있고, 실패면 errorCode·errorMessage가 있다. calls는 그동안 한 HTTP 호출 전부다
 * (호출 이력·월 사용량에 남긴다).
 */
public record FetchResult(
    WeatherReport report, List<CallRecord> calls, String errorCode, String errorMessage)
    implements ProviderResult {

  public FetchResult {
    calls = List.copyOf(calls);
  }

  public static FetchResult success(WeatherReport report, List<CallRecord> calls) {
    return new FetchResult(report, calls, null, null);
  }

  public static FetchResult failed(String errorCode, String errorMessage, List<CallRecord> calls) {
    return new FetchResult(null, calls, errorCode, errorMessage);
  }

  @Override
  public boolean ok() {
    return report != null;
  }

  /** 마지막 호출의 HTTP 상태 (없으면 null) */
  public Integer lastHttpStatus() {
    return calls.isEmpty() ? null : calls.getLast().httpStatus();
  }

  public long durationMs() {
    return calls.stream().mapToLong(CallRecord::durationMs).sum();
  }
}
