package app.wearly.weather.domain;

import java.util.List;

/**
 * 과거 기상 자료를 받은 결과. 성공이면 days가 있고, 실패면 errorCode·errorMessage가 있다. calls는 그동안 한 HTTP 호출이다 (호출 이력에
 * 남긴다).
 */
public record ClimateFetch(
    List<DailyObservation> days, List<CallRecord> calls, String errorCode, String errorMessage) {

  public ClimateFetch {
    calls = List.copyOf(calls);
    days = days == null ? null : List.copyOf(days);
  }

  public static ClimateFetch success(List<DailyObservation> days, List<CallRecord> calls) {
    return new ClimateFetch(days, calls, null, null);
  }

  public static ClimateFetch failed(String errorCode, String errorMessage, List<CallRecord> calls) {
    return new ClimateFetch(null, calls, errorCode, errorMessage);
  }

  public boolean ok() {
    return days != null;
  }
}
