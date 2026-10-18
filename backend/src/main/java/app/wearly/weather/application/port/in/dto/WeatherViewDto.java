package app.wearly.weather.application.port.in.dto;

import java.time.Instant;
import java.util.List;

/**
 * 앱 날씨 조회 결과 (GET /weather). 공급자 이름은 넣지 않는다: 앱은 출처와 상관없이 같은 모양으로 받는다.
 *
 * @param days 그 지역의 오늘부터
 * @param stale 저장 시각이 주기의 3배보다 오래됐는지
 * @param version ETag 값 (지역·저장 시각·오늘 날짜·stale이 같으면 같다)
 */
public record WeatherViewDto(
    String regionCode,
    String regionLabel,
    CurrentWeatherDto current,
    List<DailyForecastDto> days,
    Instant fetchedAt,
    boolean stale,
    String version) {}
