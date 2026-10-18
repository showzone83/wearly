package app.wearly.weather.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.weather.application.port.in.WeatherQueryUseCase;
import app.wearly.weather.application.port.in.dto.CurrentWeatherDto;
import app.wearly.weather.application.port.in.dto.DailyForecastDto;
import app.wearly.weather.application.port.in.dto.WeatherViewDto;
import app.wearly.weather.application.port.out.WeatherSettingPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherSetting;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱 날씨 조회. 저장된 값만 읽는다 (외부 API를 부르지 않는다) */
@Service
public class WeatherQueryService implements WeatherQueryUseCase {

  private final WeatherSnapshotPort snapshots;
  private final WeatherSettingPort settings;
  private final CodeCatalog catalog;
  private final Clock clock;

  public WeatherQueryService(
      WeatherSnapshotPort snapshots,
      WeatherSettingPort settings,
      CodeCatalog catalog,
      Clock clock) {
    this.snapshots = snapshots;
    this.settings = settings;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public WeatherViewDto current(String regionCode) {
    String code =
        regionCode == null || regionCode.isBlank()
            ? WeatherRegion.DEFAULT_CODE
            : regionCode.strip();
    WeatherRegion region =
        WeatherRegion.find(catalog.current(), code)
            .orElseThrow(() -> BusinessException.validation("region", FieldErrorCode.NOT_FOUND));
    WeatherSnapshot snapshot =
        snapshots
            .find(code)
            .filter(WeatherSnapshot::hasData)
            .orElseThrow(() -> new BusinessException(ErrorCode.WEATHER_UNAVAILABLE));
    Instant now = clock.instant();
    // 지역이 모두 한국이라 오늘은 한국 날짜로 본다
    LocalDate today = Zones.today(clock);
    WeatherSetting setting = settings.load().orElseGet(WeatherSetting::defaults);
    boolean stale = setting.stale(snapshot.fetchedAt(), now);
    // ETag. 오늘 날짜가 들어가 날짜가 바뀌면(지난 예보를 빼야 하므로) 같은 저장값이어도 304가 아니다. stale이 바뀔 때도 같다
    String version =
        code + "-" + snapshot.fetchedAt().toEpochMilli() + "-" + today + (stale ? "-stale" : "");
    return new WeatherViewDto(
        region.code(),
        region.label(),
        CurrentWeatherDto.from(snapshot.current()),
        DailyForecastDto.from(snapshot.daysFrom(today)),
        snapshot.fetchedAt(),
        stale,
        version);
  }
}
