package app.wearly.weather.application.port.in.dto;

/**
 * 장소 검색 결과 한 곳.
 *
 * @param label 장소 이름 (공급자가 준 이름. 시·군·구 자료로 맞춘 곳은 해운대구, 이름이 겹치면 부산 중구)
 * @param region 시·도·주 이름 (없으면 빈 문자열. 시·군·구는 상위 이름: 부산광역시, 경기도 성남시)
 * @param regionCode 시·도 이름으로 맞춘 곳이거나 국내 결과가 REGION 좌표와 30km 안이면 그 REGION 코드, 아니면 null. 시·군·구 결과는
 *     null (앱이 좌표 방식으로 날씨를 받게)
 * @param parentRegionCode 시·군·구 결과의 상위 시·도 코드 (예: BUSAN). 그 밖은 null
 */
public record PlaceCandidateDto(
    String label,
    String region,
    String country,
    double lat,
    double lon,
    String regionCode,
    String parentRegionCode) {

  /** 시·군·구가 아닌 결과 (parentRegionCode = null) */
  public PlaceCandidateDto(
      String label, String region, String country, double lat, double lon, String regionCode) {
    this(label, region, country, lat, lon, regionCode, null);
  }
}
