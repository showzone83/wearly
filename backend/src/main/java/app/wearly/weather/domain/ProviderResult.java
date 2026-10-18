package app.wearly.weather.domain;

import java.util.List;

/**
 * 공급자 한 곳에 한 번 물어본 결과 (예보 FetchResult, 장소 검색 SearchResult). 공급자 순서·차단·월 한도를 따라 부르는
 * 쪽(WeatherRefreshRunner)이 결과 종류와 상관없이 성공 여부와 호출 기록만 본다.
 */
public interface ProviderResult {

  boolean ok();

  /** 그동안 한 HTTP 호출 전부 (호출 이력·월 사용량에 남긴다) */
  List<CallRecord> calls();

  String errorCode();

  String errorMessage();
}
