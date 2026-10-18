package app.wearly.weather.application.port.in.dto;

/**
 * 날짜 하나의 날씨 요청. region 방식(regionCode, 없으면 서울) 또는 좌표 방식(lat·lon 둘 다, 로그인 필요).
 *
 * @param date 한국 날짜 YYYY-MM-DD (검증은 유스케이스가 한다)
 * @param userId 로그인한 회원. 로그인하지 않았으면 null
 */
public record WeatherDayQueryDto(
    String regionCode, Double lat, Double lon, String date, Long userId) {

  public boolean byCoordinates() {
    return lat != null || lon != null;
  }
}
