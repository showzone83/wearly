package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** 19 문서 4장: 일별 관측값 → 월 평년값 (평균 최고·최저, (최고+최저)/2 평균, 1mm 이상 비 온 날 비율) */
class MonthlyClimateTest {

  static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

  static DailyObservation day(String date, Double max, Double min, Double rain) {
    return new DailyObservation(LocalDate.parse(date), max, min, rain);
  }

  @Test
  void 월별로_평균을_내고_비_온_날_비율을_센다() {
    List<DailyObservation> days =
        List.of(
            day("2024-10-01", 20.0, 10.0, 0.0),
            day("2024-10-02", 22.0, 12.0, 1.0),
            day("2025-10-01", 18.0, 8.0, 0.9),
            day("2025-10-02", null, 9.0, 5.0),
            day("2025-01-01", 2.0, -6.0, null));

    List<MonthlyClimate> months = MonthlyClimate.compute("g", days, "2024-2025", "OPEN_METEO", NOW);

    assertThat(months).extracting(MonthlyClimate::month).containsExactly(1, 10);
    MonthlyClimate oct = months.get(1);
    assertThat(oct.tempMax()).isCloseTo(20.0, Offset.offset(1e-9));
    assertThat(oct.tempMin()).isCloseTo(9.75, Offset.offset(1e-9));
    // 최고·최저가 둘 다 있는 날만: (15 + 17 + 13) / 3
    assertThat(oct.tempAvg()).isCloseTo(15.0, Offset.offset(1e-9));
    // 1mm 이상: 10-02(1.0), 10-02(5.0) → 2/4
    assertThat(oct.rainDayRatio()).isEqualTo(0.5);
    assertThat(oct.rainProb()).isEqualTo(50);
    assertThat(oct.years()).isEqualTo("2024-2025");
    assertThat(oct.source()).isEqualTo("OPEN_METEO");
    assertThat(oct.computedAt()).isEqualTo(NOW);
    // 강수 자료가 없는 달은 비율 0
    assertThat(months.getFirst().rainDayRatio()).isZero();
  }

  @Test
  void 기온이_없는_달은_빠지고_400일이_지나면_오래된_값이다() {
    assertThat(
            MonthlyClimate.compute("g", List.of(day("2024-03-01", null, null, 3.0)), "y", "s", NOW))
        .isEmpty();

    MonthlyClimate m = new MonthlyClimate("g", 3, 1, 0, 2, 0.1, "y", "s", NOW);
    assertThat(m.fresh(NOW.plus(Duration.ofDays(399)))).isTrue();
    assertThat(m.fresh(NOW.plus(Duration.ofDays(401)))).isFalse();
  }

  @Test
  void 목업_자료로도_12개월이_나온다() {
    List<DailyObservation> days =
        MockWeather.climateDays(37.6, LocalDate.of(2016, 1, 1), LocalDate.of(2025, 12, 31));
    assertThat(days).hasSize(3653);
    List<MonthlyClimate> months = MonthlyClimate.compute("g", days, "y", "MOCK", NOW);
    assertThat(months).hasSize(12);
    // 서울 월평균표와 비슷하다 (8월이 1월보다 덥다)
    assertThat(months.get(7).tempAvg()).isGreaterThan(months.get(0).tempAvg() + 20);
  }
}
