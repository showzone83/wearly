package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunStatus;
import app.wearly.weather.domain.RunTrigger;
import app.wearly.weather.domain.SkipReason;
import app.wearly.weather.domain.WeatherOptions;
import app.wearly.weather.domain.WeatherSetting;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 17 문서 3장: 주기 실행 · 건너뛰기 · 시작 실행 (가짜 포트) */
class WeatherScheduleServiceTest {

  WeatherFakes fakes;
  WeatherScheduleService schedule;

  @BeforeEach
  void setUp() {
    fakes = new WeatherFakes();
    fakes.provider("WEATHERAPI", 1, null);
    schedule = fakes.schedule();
  }

  @Test
  void 다음_실행은_DB_주기를_매번_다시_읽는다() {
    assertThat(schedule.nextRunAt(null))
        .isEqualTo(WeatherFakes.NOW.plus(WeatherOptions.STARTUP_DELAY));
    assertThat(schedule.nextRunAt(WeatherFakes.NOW))
        .isEqualTo(WeatherFakes.NOW.plus(Duration.ofMinutes(30)));

    fakes.setting = new WeatherSetting(true, 60, 3, 3, 15, 1L, WeatherFakes.NOW);
    assertThat(schedule.nextRunAt(WeatherFakes.NOW))
        .isEqualTo(WeatherFakes.NOW.plus(Duration.ofMinutes(60)));
  }

  @Test
  void 주기_실행은_잠금을_잡고_활성_지역을_받은_뒤_푼다() {
    schedule.runScheduled(false);

    RefreshRun run = fakes.runRows.getFirst();
    assertThat(run.trigger()).isEqualTo(RunTrigger.SCHEDULE);
    assertThat(run.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(run.successCount()).isEqualTo(3);
    assertThat(fakes.lockHeld).isFalse();
    assertThat(fakes.releases).isEqualTo(1);
  }

  @Test
  void 꺼져_있거나_공급자가_없거나_다른_실행_중이면_SKIPPED로_남긴다() {
    fakes.setting = new WeatherSetting(false, 30, 3, 3, 15, null, null);
    schedule.runScheduled(false);
    assertThat(fakes.runRows.getLast().skipReason()).isEqualTo(SkipReason.DISABLED);

    fakes.setting = new WeatherSetting(true, 30, 3, 3, 15, null, null);
    fakes.keys.put("WEATHERAPI", app.wearly.weather.domain.KeyState.MISSING);
    schedule.runScheduled(false);
    assertThat(fakes.runRows.getLast().skipReason()).isEqualTo(SkipReason.NO_PROVIDER);

    fakes.keys.put("WEATHERAPI", app.wearly.weather.domain.KeyState.CONFIGURED);
    fakes.lockHeld = true;
    schedule.runScheduled(false);
    assertThat(fakes.runRows.getLast().skipReason()).isEqualTo(SkipReason.LOCKED);
    assertThat(fakes.runRows).allMatch(r -> r.status() == RunStatus.SKIPPED);
    assertThat(fakes.fetched).isEmpty();
  }

  @Test
  void 시작_실행은_저장값이_주기보다_새것이면_건너뛰고_오래됐으면_받는다() {
    schedule.runScheduled(false);
    int before = fakes.runRows.size();

    schedule.runScheduled(true);
    assertThat(fakes.runRows).hasSize(before);

    // 저장값이 없는 새 서버는 시작하자마자 받는다
    WeatherFakes fresh = new WeatherFakes();
    fresh.provider("WEATHERAPI", 1, null);
    fresh.schedule().runScheduled(true);
    assertThat(fresh.runRows.getFirst().trigger()).isEqualTo(RunTrigger.STARTUP);
    assertThat(fresh.snapshotRows).hasSize(3);
  }
}
