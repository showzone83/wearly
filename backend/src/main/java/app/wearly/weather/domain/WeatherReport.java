package app.wearly.weather.domain;

import java.util.List;
import java.util.Map;

/**
 * 공급자 한 곳에서 받은 한 지역의 날씨 (공통 모양). 현재 날씨와 예보는 늘 같은 공급자의 값이다.
 *
 * @param locationName 공급자가 알려 준 장소 이름 (확인용)
 * @param raw 공급자 원본 응답 (관리자 화면 확인용. 시간별 예보처럼 큰 부분은 어댑터가 뺀다)
 */
public record WeatherReport(
    String locationName,
    CurrentWeather current,
    List<DailyForecast> days,
    Map<String, Object> raw) {

  public WeatherReport {
    days = List.copyOf(days);
    raw = raw == null ? Map.of() : raw;
  }
}
