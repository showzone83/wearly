package app.wearly.weather.domain;

import java.util.List;

/** 공급자 한 곳에 장소를 검색한 결과. 성공이면 items가 있고(비어 있을 수 있다), 실패면 errorCode·errorMessage가 있다 */
public record SearchResult(
    List<PlaceCandidate> items, List<CallRecord> calls, String errorCode, String errorMessage)
    implements ProviderResult {

  public SearchResult {
    calls = List.copyOf(calls);
    items = items == null ? null : List.copyOf(items);
  }

  public static SearchResult success(List<PlaceCandidate> items, List<CallRecord> calls) {
    return new SearchResult(items, calls, null, null);
  }

  public static SearchResult failed(String errorCode, String errorMessage, List<CallRecord> calls) {
    return new SearchResult(null, calls, errorCode, errorMessage);
  }

  @Override
  public boolean ok() {
    return items != null;
  }
}
