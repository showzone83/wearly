package app.wearly.admin.adapter.in.web.request;

/**
 * POST /admin/weather/refresh·providers/{code}/test 본문 (17 문서 6장). 지금 실행은 regionCode가 없으면 활성 지역 전체,
 * 시험 호출은 필수다.
 */
public record WeatherRegionRequest(String regionCode) {

  public static final WeatherRegionRequest EMPTY = new WeatherRegionRequest(null);
}
