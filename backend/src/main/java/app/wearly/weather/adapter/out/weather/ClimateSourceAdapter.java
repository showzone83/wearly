package app.wearly.weather.adapter.out.weather;

import app.wearly.common.config.WearlyProperties;
import app.wearly.weather.adapter.out.weather.openmeteo.OpenMeteoClient;
import app.wearly.weather.application.port.out.ClimateSourcePort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.ClimateFetch;
import app.wearly.weather.domain.MockWeather;
import app.wearly.weather.domain.WeatherOptions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * ClimateSourcePort 구현: Open-Meteo 과거 기상 API (설정 wearly.weather.climate.base-url·api-key). 목업 실행
 * (WEARLY_WEATHER_PROVIDER=mock)이면 목업 자료를 준다 (호출 기록은 같은 모양으로 남는다).
 */
@Component
class ClimateSourceAdapter implements ClimateSourcePort {

  static final String DEFAULT_BASE_URL = "https://archive-api.open-meteo.com";

  private final WeatherOptions options;
  private final Clock clock;
  private final OpenMeteoClient client;

  ClimateSourceAdapter(
      WearlyProperties properties, WeatherOptions options, JsonMapper json, Clock clock) {
    WearlyProperties.Weather w = properties.weather();
    String base = w.climate().baseUrl();
    this.options = options;
    this.clock = clock;
    this.client =
        new OpenMeteoClient(
            base == null || base.isBlank() ? DEFAULT_BASE_URL : base,
            w.climate().apiKey(),
            w.timeout(),
            json,
            clock);
  }

  @Override
  public String sourceCode() {
    return options.mock() ? "MOCK" : PROVIDER_CODE;
  }

  @Override
  public ClimateFetch fetch(String target, double lat, double lon, LocalDate from, LocalDate to) {
    if (options.mock()) {
      Instant now = clock.instant();
      return ClimateFetch.success(
          MockWeather.climateDays(lat, from, to),
          List.of(
              new CallRecord(
                  target,
                  PROVIDER_CODE,
                  CallRecord.CLIMATE,
                  false,
                  200,
                  true,
                  null,
                  null,
                  0,
                  0,
                  now)));
    }
    return client.fetch(target, lat, lon, from, to);
  }
}
