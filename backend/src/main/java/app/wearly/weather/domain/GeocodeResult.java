package app.wearly.weather.domain;

import java.util.List;

/**
 * 지오코딩을 한 번 부른 결과. 성공이면 places가 있고(비어 있을 수 있다), 실패면 errorCode·errorMessage가 있다. calls는 그동안 한 HTTP
 * 호출이다 (호출 이력에 SEARCH로 남긴다).
 */
public record GeocodeResult(
    List<GeoPlace> places, List<CallRecord> calls, String errorCode, String errorMessage) {

  public GeocodeResult {
    calls = List.copyOf(calls);
    places = places == null ? null : List.copyOf(places);
  }

  public static GeocodeResult success(List<GeoPlace> places, List<CallRecord> calls) {
    return new GeocodeResult(places, calls, null, null);
  }

  public static GeocodeResult failed(
      String errorCode, String errorMessage, List<CallRecord> calls) {
    return new GeocodeResult(null, calls, errorCode, errorMessage);
  }

  public boolean ok() {
    return places != null;
  }
}
