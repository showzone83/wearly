package app.wearly.weather.adapter.out.weather;

import app.wearly.common.config.WearlyProperties;
import app.wearly.common.domain.Zones;
import app.wearly.weather.adapter.out.weather.weatherapi.WeatherApiClient;
import app.wearly.weather.application.port.out.WeatherSourcePort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.KeyState;
import app.wearly.weather.domain.MockWeather;
import app.wearly.weather.domain.WeatherOptions;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * WeatherSourcePort 구현: 공급자 코드별 HTTP 클라이언트로 보낸다. 지금은 WEATHERAPI만 있다 (OpenWeatherMap은 클라이언트와 공급자 행을
 * 더해 붙인다). 목업 실행이면 모든 공급자를 목업 날씨로 대신한다.
 *
 * <p>키 찾는 순서: 서버 설정 wearly.weather.keys.&lt;키 이름&gt; → 같은 이름의 OS 환경변수.
 */
@Component
class WeatherSourceAdapter implements WeatherSourcePort {

  private final WearlyProperties.Weather properties;
  private final WeatherOptions options;
  private final Clock clock;
  private final Map<String, WeatherApiClient> clients;

  WeatherSourceAdapter(
      WearlyProperties properties, WeatherOptions options, JsonMapper json, Clock clock) {
    this.properties = properties.weather();
    this.options = options;
    this.clock = clock;
    String base = this.properties.baseUrls().get(WeatherApiClient.CODE);
    this.clients =
        Map.of(
            WeatherApiClient.CODE,
            new WeatherApiClient(
                base == null || base.isBlank() ? "https://api.weatherapi.com/v1" : base,
                this.properties.timeout(),
                json,
                clock));
  }

  @Override
  public KeyState keyState(WeatherProvider provider) {
    if (options.mock()) {
      return KeyState.MOCK;
    }
    if (!clients.containsKey(provider.code())) {
      return KeyState.UNSUPPORTED;
    }
    return key(provider.keyName()) == null ? KeyState.MISSING : KeyState.CONFIGURED;
  }

  @Override
  public int callsPerFetch(String providerCode) {
    return 1;
  }

  @Override
  public FetchResult fetch(WeatherProvider provider, WeatherRegion region, int days) {
    Instant now = clock.instant();
    if (options.mock()) {
      return FetchResult.success(
          MockWeather.report(region, Zones.today(clock), days, now),
          List.of(
              new CallRecord(
                  region.code(),
                  provider.code(),
                  CallRecord.FORECAST,
                  false,
                  200,
                  true,
                  null,
                  null,
                  0,
                  0,
                  now)));
    }
    WeatherApiClient client = clients.get(provider.code());
    String key = key(provider.keyName());
    if (client == null || key == null) {
      // keyState로 미리 거르므로 오지 않는다. 오면 호출 없이 실패로 돌려준다
      return FetchResult.failed(
          FetchErrors.CLIENT, provider.displayName() + ": 키 또는 구현이 없어요.", List.of());
    }
    return client.fetch(key, region, days);
  }

  private String key(String keyName) {
    if (keyName == null || keyName.isBlank()) {
      return null;
    }
    String v = properties.keys().get(keyName);
    if (v == null || v.isBlank()) {
      v = System.getenv(keyName);
    }
    return v == null || v.isBlank() ? null : v.strip();
  }
}
