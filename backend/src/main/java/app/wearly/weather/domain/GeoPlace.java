package app.wearly.weather.domain;

/**
 * 지오코딩 결과 한 곳 (Open-Meteo 지오코딩, 정렬 전). 정렬·중복 제거는 PlaceSearchRules가 하고, 앱에는 PlaceCandidate 모양으로 준다.
 *
 * @param name 장소 이름 (language=ko면 한글, 예: 도쿄·강릉시. 한글 이름이 없으면 원어)
 * @param admin1 시·도·주 이름 (없으면 null)
 * @param country 나라 이름 (예: 대한민국·일본)
 * @param countryCode ISO 3166-1 alpha-2 (예: KR·KP·JP, 없으면 null)
 * @param featureCode GeoNames 지형 코드 (PPLC 수도, PPLA~PPLA5 행정 중심, PPL 도시·마을, AIRP 공항, PRK 공원 …)
 * @param population 인구 (모르면 null)
 */
public record GeoPlace(
    String name,
    String admin1,
    String country,
    String countryCode,
    String featureCode,
    Long population,
    double lat,
    double lon) {

  /** 대한민국 국가 코드 */
  public static final String KOREA = "KR";

  public boolean korea() {
    return KOREA.equals(countryCode);
  }

  public PlaceCandidate toCandidate() {
    return new PlaceCandidate(
        name == null ? "" : name,
        admin1 == null ? "" : admin1,
        country == null ? "" : country,
        lat,
        lon);
  }
}
