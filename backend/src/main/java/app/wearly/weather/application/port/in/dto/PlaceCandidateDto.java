package app.wearly.weather.application.port.in.dto;

/**
 * 장소 검색 결과 한 곳.
 *
 * @param label 장소 이름 (공급자가 준 이름)
 * @param region 시·도·주 이름 (없으면 빈 문자열)
 * @param regionCode 국내 결과가 REGION 좌표와 30km 안이면 그 REGION 코드, 아니면 null
 */
public record PlaceCandidateDto(
    String label, String region, String country, double lat, double lon, String regionCode) {}
