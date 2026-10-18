package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.weather.domain.CurrentWeather;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.ProviderStatus;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunStatus;
import app.wearly.weather.domain.RunTrigger;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherHistory;
import app.wearly.weather.domain.WeatherReport;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 17 문서 4장: 지역마다 공급자를 priority 순으로 시도, 차단·한도·키 문제는 건너뛴다 (가짜 포트) */
class WeatherRefreshRunnerTest {

  WeatherFakes fakes;
  WeatherRefreshRunner runner;

  @BeforeEach
  void setUp() {
    fakes = new WeatherFakes();
    runner = fakes.runner();
  }

  RefreshRun run() {
    RefreshRun run =
        fakes.runs.create(RefreshRun.start(RunTrigger.MANUAL, 1L, null, WeatherFakes.NOW));
    return runner.executeSafely(run, fakes.regions);
  }

  @Test
  void 모두_받으면_저장하고_SUCCESS다() {
    fakes.provider("WEATHERAPI", 1, null);

    RefreshRun done = run();

    assertThat(done.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(done.successCount()).isEqualTo(3);
    assertThat(done.fallbackCount()).isZero();
    assertThat(fakes.snapshotRows).containsOnlyKeys("SEOUL", "BUSAN", "JEJU");
    WeatherSnapshot seoul = fakes.snapshotRows.get("SEOUL");
    assertThat(seoul.providerCode()).isEqualTo("WEATHERAPI");
    assertThat(seoul.fetchedAt()).isEqualTo(WeatherFakes.NOW);
    assertThat(fakes.callRows).hasSize(3).allMatch(c -> !c.fallback());
    assertThat(fakes.runRows.getFirst().status()).isEqualTo(RunStatus.SUCCESS);
  }

  @Test
  void 받은_지역은_오늘_이력에_예보와_관측을_남긴다() {
    fakes.provider("WEATHERAPI", 1, null);

    run();

    assertThat(fakes.historyRows)
        .containsOnlyKeys("SEOUL:2026-10-17", "BUSAN:2026-10-17", "JEJU:2026-10-17");
    WeatherHistory seoul = fakes.history("SEOUL", "2026-10-17");
    assertThat(seoul.providerCode()).isEqualTo("WEATHERAPI");
    assertThat(seoul.forecast().max()).isEqualTo(21);
    assertThat(seoul.forecastFetchedAt()).isEqualTo(WeatherFakes.NOW);
    assertThat(seoul.observedCount()).isEqualTo(1);
    assertThat(seoul.observedMin()).isEqualTo(18.5);

    // 같은 관측 시각을 다시 받으면 관측 수는 그대로다
    run();
    assertThat(fakes.history("SEOUL", "2026-10-17").observedCount()).isEqualTo(1);
  }

  @Test
  void 자정_전_관측은_전날_이력에_더하고_예보는_오늘_이력에_남긴다() {
    fakes.provider("WEATHERAPI", 1, null);
    // 2026-10-16 23:55 KST에 관측한 값
    Instant lateNight = Instant.parse("2026-10-16T14:55:00Z");
    fakes.answers.put(
        "WEATHERAPI",
        (p, r) -> {
          FetchResult ok = fakes.ok(p, r);
          WeatherReport report = ok.report();
          return FetchResult.success(
              new WeatherReport(
                  report.locationName(),
                  new CurrentWeather(9, null, WeatherCondition.RAIN, "비", lateNight),
                  report.days(),
                  report.raw()),
              ok.calls());
        });

    run();

    WeatherHistory yesterday = fakes.history("SEOUL", "2026-10-16");
    assertThat(yesterday.observedCount()).isEqualTo(1);
    assertThat(yesterday.observedRainCount()).isEqualTo(1);
    assertThat(yesterday.forecast()).isNull();
    WeatherHistory today = fakes.history("SEOUL", "2026-10-17");
    assertThat(today.forecast()).isNotNull();
    assertThat(today.observedCount()).isZero();
  }

  @Test
  void 실패한_지역은_이력을_남기지_않는다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.answers.put("WEATHERAPI", WeatherFakes.failing(fakes, FetchErrors.TIMEOUT, null));

    run();

    assertThat(fakes.historyRows).isEmpty();
  }

  @Test
  void 이력_저장이_실패해도_저장값과_실행은_그대로다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.historyFails = true;

    RefreshRun done = run();

    assertThat(done.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(fakes.snapshotRows).containsOnlyKeys("SEOUL", "BUSAN", "JEJU");
    assertThat(fakes.historyRows).isEmpty();
  }

  @Test
  void 연속_실패가_기준에_닿으면_차단하고_남은_지역은_부르지_않는다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.setting = new app.wearly.weather.domain.WeatherSetting(true, 30, 3, 2, 15, null, null);
    fakes.answers.put("WEATHERAPI", WeatherFakes.failing(fakes, FetchErrors.HTTP_5XX, 503));

    RefreshRun done = run();

    assertThat(done.status()).isEqualTo(RunStatus.FAILED);
    assertThat(done.failCount()).isEqualTo(3);
    // 2번 실패로 차단 → 세 번째 지역(JEJU)은 부르지 않는다
    assertThat(fakes.fetched).containsExactly("WEATHERAPI:SEOUL", "WEATHERAPI:BUSAN");
    assertThat(fakes.providerRows.get("WEATHERAPI").health().status(WeatherFakes.NOW))
        .isEqualTo(ProviderStatus.OPEN);
    assertThat(fakes.snapshotRows.get("JEJU").lastError()).contains("부를 수 있는 공급자가 없어요");
    assertThat(fakes.snapshotRows.get("SEOUL").lastError()).contains("HTTP_5XX");
  }

  @Test
  void 실패해도_이전_저장값은_그대로_두고_오류만_남긴다() {
    fakes.provider("WEATHERAPI", 1, null);
    run();
    WeatherSnapshot before = fakes.snapshotRows.get("SEOUL");

    fakes.answers.put("WEATHERAPI", WeatherFakes.failing(fakes, FetchErrors.TIMEOUT, null));
    RefreshRun second =
        runner.executeSafely(
            fakes.runs.create(RefreshRun.start(RunTrigger.SCHEDULE, null, null, WeatherFakes.NOW)),
            fakes.regions.subList(0, 1));

    assertThat(second.status()).isEqualTo(RunStatus.FAILED);
    WeatherSnapshot after = fakes.snapshotRows.get("SEOUL");
    assertThat(after.current()).isEqualTo(before.current());
    assertThat(after.fetchedAt()).isEqualTo(before.fetchedAt());
    assertThat(after.lastError()).contains("TIMEOUT");
  }

  @Test
  void 인증_오류는_바로_키_확인_필요로_막는다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.answers.put("WEATHERAPI", WeatherFakes.failing(fakes, FetchErrors.AUTH, 401));

    run();

    assertThat(fakes.fetched).containsExactly("WEATHERAPI:SEOUL");
    assertThat(fakes.providerRows.get("WEATHERAPI").health().status(WeatherFakes.NOW))
        .isEqualTo(ProviderStatus.KEY_INVALID);
  }

  @Test
  void 월_한도에_닿은_공급자는_건너뛴다() {
    fakes.provider("WEATHERAPI", 1, 2);

    RefreshRun done = run();

    assertThat(fakes.callsTo("WEATHERAPI")).isEqualTo(2);
    assertThat(done.status()).isEqualTo(RunStatus.PARTIAL);
    assertThat(done.successCount()).isEqualTo(2);
    assertThat(done.failCount()).isEqualTo(1);
  }

  @Test
  void 키가_없는_공급자는_부르지_않는다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.keys.put("WEATHERAPI", app.wearly.weather.domain.KeyState.MISSING);

    assertThat(runner.anyUsable()).isFalse();
    RefreshRun done = run();

    assertThat(fakes.fetched).isEmpty();
    assertThat(done.status()).isEqualTo(RunStatus.FAILED);
  }

  /** 예비 공급자를 붙였을 때의 순서 규칙 (지금은 공급자가 하나지만 순서·전환 로직은 공급자 수와 상관없다) */
  @Test
  void 우선순위_공급자가_실패하면_같은_지역을_다음_공급자로_받고_예비로_센다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.provider("BACKUP", 2, null);
    fakes.answers.put("WEATHERAPI", WeatherFakes.failing(fakes, FetchErrors.HTTP_5XX, 500));

    RefreshRun done = run();

    assertThat(done.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(done.fallbackCount()).isEqualTo(3);
    assertThat(fakes.snapshotRows.get("SEOUL").providerCode()).isEqualTo("BACKUP");
    // 3번 실패 기준: SEOUL·BUSAN·JEJU에서 1순위를 한 번씩 부른 뒤 차단
    assertThat(fakes.fetched)
        .containsExactly(
            "WEATHERAPI:SEOUL",
            "BACKUP:SEOUL",
            "WEATHERAPI:BUSAN",
            "BACKUP:BUSAN",
            "WEATHERAPI:JEJU",
            "BACKUP:JEJU");
    assertThat(fakes.callRows.stream().filter(c -> c.providerCode().equals("BACKUP")))
        .allMatch(c -> c.fallback());
  }

  @Test
  void 어댑터가_예외를_던져도_실패로_기록하고_실행은_끝난다() {
    fakes.provider("WEATHERAPI", 1, null);
    fakes.answers.put(
        "WEATHERAPI",
        (p, r) -> {
          throw new IllegalStateException("boom");
        });

    RefreshRun done = run();

    assertThat(done.status()).isEqualTo(RunStatus.FAILED);
    assertThat(fakes.callRows).isNotEmpty().allMatch(c -> FetchErrors.CLIENT.equals(c.errorCode()));
  }
}
