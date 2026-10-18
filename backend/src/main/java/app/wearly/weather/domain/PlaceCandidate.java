package app.wearly.weather.domain;

/**
 * 장소 검색 결과 한 곳 (공급자와 상관없는 모양).
 *
 * @param label 장소 이름 (지오코딩이면 한글 이름, 예: 도쿄. 날씨 공급자 검색으로 대신했으면 영어, 예: Tokyo)
 * @param region 시·도·주 이름 (없으면 빈 문자열)
 * @param country 나라 이름
 * @param major 행정 중심이거나 인구가 있는 곳. 이런 곳에만 30km 안의 REGION 코드를 붙인다 (경북의 작은 마을 'Pusan'에 GYEONGBUK가 붙지
 *     않게). 날씨 공급자 검색 결과는 도시라고 보고 true
 */
public record PlaceCandidate(
    String label, String region, String country, double lat, double lon, boolean major) {

  /** 날씨 공급자 검색 결과처럼 규모를 모르는 곳 (major = true) */
  public PlaceCandidate(String label, String region, String country, double lat, double lon) {
    this(label, region, country, lat, lon, true);
  }
}
