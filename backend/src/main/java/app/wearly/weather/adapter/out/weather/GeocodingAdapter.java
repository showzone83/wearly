package app.wearly.weather.adapter.out.weather;

import app.wearly.common.config.WearlyProperties;
import app.wearly.weather.adapter.out.weather.openmeteo.OpenMeteoGeocodingClient;
import app.wearly.weather.application.port.out.GeocodingPort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.GeocodeResult;
import app.wearly.weather.domain.MockWeather;
import app.wearly.weather.domain.WeatherOptions;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * GeocodingPort 구현: Open-Meteo 지오코딩 API (설정 wearly.weather.geocoding.enabled·base-url·api-key). 목업
 * 실행 (WEARLY_WEATHER_PROVIDER=mock)이면 목업 장소 목록을 준다 (호출 기록은 같은 모양으로 남는다).
 */
@Component
class GeocodingAdapter implements GeocodingPort {

  static final String DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com";

  private final boolean enabled;
  private final WeatherOptions options;
  private final Clock clock;
  private final OpenMeteoGeocodingClient client;

  GeocodingAdapter(
      WearlyProperties properties, WeatherOptions options, JsonMapper json, Clock clock) {
    WearlyProperties.Weather w = properties.weather();
    this.enabled = w.geocoding().enabled();
    this.options = options;
    this.clock = clock;
    String base = w.geocoding().baseUrl();
    this.client =
        new OpenMeteoGeocodingClient(
            base == null || base.isBlank() ? DEFAULT_BASE_URL : base,
            w.geocoding().apiKey(),
            w.timeout(),
            json,
            clock);
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public GeocodeResult search(String query) {
    if (options.mock()) {
      return GeocodeResult.success(
          MockWeather.geocode(query),
          List.of(
              new CallRecord(
                  null,
                  PROVIDER_CODE,
                  CallRecord.SEARCH,
                  false,
                  200,
                  true,
                  null,
                  null,
                  0,
                  0,
                  clock.instant())));
    }
    return client.search(query);
  }
}
