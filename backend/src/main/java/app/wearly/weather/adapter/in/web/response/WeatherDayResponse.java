package app.wearly.weather.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.ClimateInfoDto;
import app.wearly.weather.application.port.in.dto.DayWeatherDto;
import app.wearly.weather.application.port.in.dto.PlaceDto;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;

/**
 * GET /weather/day 응답 (19 문서). 기온은 섭씨 소수 첫째 자리, 날짜는 YYYY-MM-DD, 시각은 ISO-8601 UTC. 공급자 이름은 넣지 않는다.
 *
 * @param source FORECAST · CLIMATE
 * @param forecastUntil 이 장소 예보의 마지막 날짜 (없으면 null)
 * @param climate CLIMATE일 때만, 아니면 null
 */
public record WeatherDayResponse(
    Place place,
    String date,
    String source,
    Day day,
    String forecastUntil,
    String fetchedAt,
    Climate climate) {

  /**
   * @param regionCode 좌표 요청이면 null
   * @param lat REGION 좌표, 좌표 요청이면 격자 좌표(소수 첫째 자리)
   */
  public record Place(String regionCode, String label, double lat, double lon) {

    static Place from(PlaceDto p) {
      return new Place(p.regionCode(), p.label(), p.lat(), p.lon());
    }
  }

  /**
   * @param cond SUNNY · CLOUDY · RAIN. CLIMATE면 null
   * @param condText CLIMATE면 null
   */
  public record Day(
      double temp, double min, double max, int rainProb, String cond, String condText) {

    static Day from(DayWeatherDto d) {
      return new Day(
          WeatherResponse.round(d.temp()),
          WeatherResponse.round(d.min()),
          WeatherResponse.round(d.max()),
          d.rainProb(),
          d.cond(),
          d.condText());
    }
  }

  /**
   * @param years 예: "2016-2025"
   * @param rainDayRatio 강수 1mm 이상인 날의 비율 (소수 둘째 자리)
   */
  public record Climate(String years, double rainDayRatio) {

    static Climate from(ClimateInfoDto c) {
      return c == null ? null : new Climate(c.years(), Math.round(c.rainDayRatio() * 100) / 100.0);
    }
  }

  public static WeatherDayResponse from(WeatherDayDto v) {
    return new WeatherDayResponse(
        Place.from(v.place()),
        v.date().toString(),
        v.source(),
        Day.from(v.day()),
        v.forecastUntil() == null ? null : v.forecastUntil().toString(),
        Times.iso(v.fetchedAt()),
        Climate.from(v.climate()));
  }
}
