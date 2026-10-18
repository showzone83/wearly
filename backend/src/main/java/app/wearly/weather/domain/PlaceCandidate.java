package app.wearly.weather.domain;

/**
 * 장소 검색 결과 한 곳 (공급자와 상관없는 모양).
 *
 * @param label 장소 이름 (공급자가 준 이름, 예: Tokyo)
 * @param region 시·도·주 이름 (없으면 빈 문자열)
 * @param country 나라 이름
 */
public record PlaceCandidate(String label, String region, String country, double lat, double lon) {}
