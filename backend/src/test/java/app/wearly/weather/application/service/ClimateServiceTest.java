package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.MonthlyClimate;
import app.wearly.weather.domain.PlaceWeatherOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 19 문서 4장: 평년값은 처음 필요할 때 12개월을 한 번에 계산해 두고, 400일이 지나면 다시 계산한다 (가짜 포트) */
class ClimateServiceTest {

  static final Instant NOW = WeatherFakes.NOW;
  static final GridKey GRID = GridKey.of(35.69, 139.69);

  PlaceWeatherFakes fakes;
  ClimateService climates;

  @BeforeEach
  void setUp() {
    fakes = new PlaceWeatherFakes();
    climates = fakes.climateService();
  }

  MonthlyClimate row(int month, Instant computedAt) {
    return new MonthlyClimate(
        GRID.key(), month, 1, 0, 2, 0.1, "2014-2023", "OPEN_METEO", computedAt);
  }

  @Test
  void 처음_필요할_때_계산해_저장하고_다음에는_DB에서_읽는다() {
    Optional<MonthlyClimate> oct = climates.monthly(GRID, 10);

    assertThat(oct).isPresent();
    assertThat(oct.get().tempAvg()).isEqualTo(15);
    assertThat(oct.get().years()).isEqualTo("2016-2025");
    assertThat(oct.get().source()).isEqualTo("OPEN_METEO");
    assertThat(fakes.climateFetches).containsExactly("35.7,139.7@2016-01-01~2025-12-31");
    assertThat(fakes.climateRows).containsOnlyKeys("35.7,139.7:1", "35.7,139.7:10");
    assertThat(fakes.base.callRows)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.callKind()).isEqualTo(CallRecord.CLIMATE);
              assertThat(c.regionCode()).isEqualTo("35.7,139.7");
            });
    assertThat(fakes.base.callRunIds).containsOnlyNulls();

    assertThat(climates.monthly(GRID, 1)).isPresent();
    assertThat(climates.monthly(GRID, 10)).isPresent();
    assertThat(fakes.climateFetches).hasSize(1);
    // 자료가 없는 달(3월)은 비어 있다 (요청 때 다시 계산을 시도한다)
    assertThat(climates.monthly(GRID, 3)).isEmpty();
    assertThat(fakes.climateFetches).hasSize(2);
  }

  @Test
  void 계산한_지_400일이_넘으면_다시_계산한다() {
    fakes.climateRows.put("35.7,139.7:10", row(10, NOW.minus(Duration.ofDays(399))));
    assertThat(climates.monthly(GRID, 10).get().years()).isEqualTo("2014-2023");
    assertThat(fakes.climateFetches).isEmpty();

    fakes.climateRows.put("35.7,139.7:10", row(10, NOW.minus(Duration.ofDays(401))));
    MonthlyClimate again = climates.monthly(GRID, 10).get();
    assertThat(again.years()).isEqualTo("2016-2025");
    assertThat(again.computedAt()).isEqualTo(NOW);
    assertThat(fakes.climateFetches).hasSize(1);
  }

  @Test
  void 받기에_실패하면_예전_값을_쓰고_10분_동안_다시_부르지_않는다() {
    fakes.climateAnswer = fakes::climateFailed;
    fakes.climateRows.put("35.7,139.7:10", row(10, NOW.minus(Duration.ofDays(500))));

    assertThat(climates.monthly(GRID, 10).get().years()).isEqualTo("2014-2023");
    assertThat(climates.monthly(GRID, 10)).isPresent();
    assertThat(fakes.climateFetches).hasSize(1);
    assertThat(fakes.base.callRows.getFirst().success()).isFalse();
    assertThat(fakes.climateSaves.get()).isZero();

    // 다른 격자는 따로 부른다
    assertThat(climates.monthly(GridKey.of(1, 1), 10)).isEmpty();
    assertThat(fakes.climateFetches).hasSize(2);
  }

  @Test
  void 어댑터가_예외를_던져도_CLIENT_호출로_남긴다() {
    fakes.climateAnswer =
        () -> {
          throw new IllegalStateException("boom");
        };

    assertThat(climates.monthly(GRID, 10)).isEmpty();
    CallRecord c = fakes.base.callRows.getFirst();
    assertThat(c.errorCode()).isEqualTo(FetchErrors.CLIENT);
    assertThat(c.providerCode()).isEqualTo("OPEN_METEO");
    assertThat(c.callKind()).isEqualTo(CallRecord.CLIMATE);
    assertThat(fakes.lockRows).isEmpty();
  }

  @Test
  void 꺼져_있으면_저장된_값도_주지_않는다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 100, false, 10, Duration.ofSeconds(2));
    climates = fakes.climateService();
    fakes.climateRows.put("35.7,139.7:10", row(10, NOW));

    assertThat(climates.monthly(GRID, 10)).isEmpty();
    assertThat(fakes.climateFetches).isEmpty();
  }

  @Test
  void 설정한_연도_수만큼_받는다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 100, true, 3, Duration.ofSeconds(2));
    fakes.climateService().monthly(GRID, 10);
    assertThat(fakes.climateFetches).containsExactly("35.7,139.7@2023-01-01~2025-12-31");
  }
}
