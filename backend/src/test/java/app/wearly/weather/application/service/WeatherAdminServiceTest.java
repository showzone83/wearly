package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunStatus;
import app.wearly.weather.domain.RunTrigger;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 17 문서 6장: 관리자 "지금 실행" (가짜 포트) */
class WeatherAdminServiceTest {

  WeatherFakes fakes;

  @BeforeEach
  void setUp() {
    fakes = new WeatherFakes();
    fakes.provider("WEATHERAPI", 1, null);
  }

  @Test
  void 지금_실행은_실행_스레드에서_받고_잠금을_푼다() {
    long runId = fakes.admin().requestRefresh(1L, null);

    RefreshRun run = fakes.runRows.get((int) (runId - 1));
    assertThat(run.trigger()).isEqualTo(RunTrigger.MANUAL);
    assertThat(run.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(run.successCount()).isEqualTo(3);
    assertThat(fakes.lockHeld).isFalse();
  }

  @Test
  void 실행_스레드에_넘기지_못하면_FAILED로_닫고_잠금을_푼다() {
    fakes.executor =
        task -> {
          throw new RejectedExecutionException("꽉 참");
        };

    assertThatThrownBy(() -> fakes.admin().requestRefresh(1L, null))
        .isInstanceOf(RejectedExecutionException.class);

    RefreshRun run = fakes.runRows.getFirst();
    assertThat(run.status()).isEqualTo(RunStatus.FAILED);
    assertThat(run.finishedAt()).isNotNull();
    assertThat(fakes.fetched).isEmpty();
    assertThat(fakes.lockHeld).isFalse();
    assertThat(fakes.releases).isEqualTo(1);
  }
}
