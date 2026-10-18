package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherSettingTest {

  static final Instant T0 = Instant.parse("2026-10-17T00:00:00Z");

  @Test
  void 범위를_벗어난_값은_필드_오류다() {
    assertThat(WeatherSetting.validate(30, 3, 3, 15)).isEmpty();
    List<FieldError> errors = WeatherSetting.validate(9, 15, 0, 1441);
    assertThat(errors)
        .extracting(FieldError::field)
        .containsExactly("intervalMinutes", "forecastDays", "failureThreshold", "cooldownMinutes");
    assertThat(WeatherSetting.validate(1440, 14, 20, 1440)).isEmpty();
    assertThat(WeatherSetting.validate(10, 1, 1, 1)).isEmpty();
  }

  @Test
  void 다음_실행은_직전_실행이_끝난_시각_더하기_주기이고_오래됨은_주기의_3배다() {
    WeatherSetting s = new WeatherSetting(true, 30, 3, 3, 15, null, null);
    assertThat(s.nextRunAfter(T0)).isEqualTo(T0.plusSeconds(30 * 60));
    assertThat(s.stale(T0, T0.plusSeconds(90 * 60))).isFalse();
    assertThat(s.stale(T0, T0.plusSeconds(90 * 60 + 1))).isTrue();
    assertThat(s.stale(null, T0)).isTrue();
  }

  @Test
  void 예보_일수는_설정과_공급자_최대_중_작은_쪽이다() {
    WeatherSetting s = new WeatherSetting(true, 30, 7, 3, 15, null, null);
    WeatherProvider p =
        new WeatherProvider(
            "WEATHERAPI", "W", true, 1, "K", null, 3, ProviderHealth.fresh(), null, null);
    assertThat(s.daysFor(p)).isEqualTo(3);
    assertThat(new WeatherSetting(true, 30, 2, 3, 15, null, null).daysFor(p)).isEqualTo(2);
  }

  @Test
  void 목업_날씨는_같은_날짜와_지역이면_늘_같다() {
    WeatherRegion seoul = new WeatherRegion("SEOUL", "서울", 37.5665, 126.978, true);
    LocalDate day = LocalDate.parse("2026-10-17");
    WeatherReport a = MockWeather.report(seoul, day, 3, T0);
    WeatherReport b = MockWeather.report(seoul, day, 3, T0);
    assertThat(a.days()).hasSize(3).isEqualTo(b.days());
    assertThat(a.days().getFirst().date()).isEqualTo(day);
    a.days()
        .forEach(
            d -> {
              assertThat(d.min()).isLessThanOrEqualTo(d.temp());
              assertThat(d.max()).isGreaterThanOrEqualTo(d.temp());
              assertThat(d.rainProb()).isBetween(0, 100);
            });
  }
}
