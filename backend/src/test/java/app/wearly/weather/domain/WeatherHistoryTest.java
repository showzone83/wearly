package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 17 문서 2장: 날씨 이력은 예보를 덮어쓰고 관측은 늦은 것만 더한다 */
class WeatherHistoryTest {

  static final LocalDate DAY = LocalDate.parse("2026-10-17");
  static final Instant T0 = Instant.parse("2026-10-17T00:00:00Z");

  static CurrentWeather current(double temp, WeatherCondition cond) {
    return new CurrentWeather(temp, null, cond, cond.name(), null);
  }

  static DailyForecast day(double temp) {
    return new DailyForecast(DAY, temp, temp - 5, temp + 5, 30, WeatherCondition.CLOUDY, "흐림");
  }

  @Test
  void 관측을_더하면_최저_최고_평균_비_횟수가_쌓인다() {
    WeatherHistory h =
        WeatherHistory.empty("SEOUL", DAY)
            .observe(current(10, WeatherCondition.SUNNY), T0)
            .observe(current(16, WeatherCondition.RAIN), T0.plusSeconds(1800))
            .observe(current(13, WeatherCondition.RAIN), T0.plusSeconds(3600));

    assertThat(h.observedMin()).isEqualTo(10);
    assertThat(h.observedMax()).isEqualTo(16);
    assertThat(h.observedCount()).isEqualTo(3);
    assertThat(h.observedAvg()).isEqualTo(13);
    assertThat(h.observedRainCount()).isEqualTo(2);
    assertThat(h.firstObservedAt()).isEqualTo(T0);
    assertThat(h.lastObservedAt()).isEqualTo(T0.plusSeconds(3600));
  }

  @Test
  void 이미_더한_관측과_같거나_이른_관측은_세지_않는다() {
    WeatherHistory once =
        WeatherHistory.empty("SEOUL", DAY).observe(current(10, WeatherCondition.SUNNY), T0);

    assertThat(once.observe(current(30, WeatherCondition.SUNNY), T0)).isEqualTo(once);
    assertThat(once.observe(current(30, WeatherCondition.SUNNY), T0.minusSeconds(60)))
        .isEqualTo(once);
    assertThat(WeatherHistory.empty("SEOUL", DAY).observedAvg()).isNull();
  }

  @Test
  void 예보는_마지막_값으로_덮어쓰고_관측은_그대로_둔다() {
    WeatherHistory h =
        WeatherHistory.empty("SEOUL", DAY)
            .withForecast("WEATHERAPI", day(15), T0)
            .observe(current(12, WeatherCondition.SUNNY), T0)
            .withForecast("BACKUP", day(17), T0.plusSeconds(1800));

    assertThat(h.forecast().temp()).isEqualTo(17);
    assertThat(h.providerCode()).isEqualTo("BACKUP");
    assertThat(h.forecastFetchedAt()).isEqualTo(T0.plusSeconds(1800));
    assertThat(h.observedCount()).isEqualTo(1);
  }

  @Test
  void 이력_날짜는_한국_날짜다() {
    // 2026-10-16 23:55 KST
    assertThat(WeatherHistory.dateOf(Instant.parse("2026-10-16T14:55:00Z")))
        .isEqualTo(LocalDate.parse("2026-10-16"));
    // 2026-10-17 00:05 KST
    assertThat(WeatherHistory.dateOf(Instant.parse("2026-10-16T15:05:00Z"))).isEqualTo(DAY);
    assertThat(WeatherHistory.observedAt(current(1, WeatherCondition.SUNNY), T0)).isEqualTo(T0);
  }
}
