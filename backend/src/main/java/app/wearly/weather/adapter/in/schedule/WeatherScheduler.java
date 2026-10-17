package app.wearly.weather.adapter.in.schedule;

import app.wearly.weather.application.port.in.WeatherScheduleUseCase;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * 날씨 주기 실행 (17 문서 3장). 다음 실행 시각 = 직전 실행이 끝난 시각 + DB의 주기라서, 관리자가 주기를 바꾸면 재시작 없이 다음 실행부터 반영된다. 서버 시작
 * 직후 첫 실행은 저장된 값이 오래됐을 때만 받는다. wearly.weather.scheduler-enabled=false면 만들지 않는다.
 */
@Component
@ConditionalOnProperty(
    name = "wearly.weather.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
class WeatherScheduler implements SchedulingConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WeatherScheduler.class);

  private final WeatherScheduleUseCase schedule;
  private final AtomicBoolean started = new AtomicBoolean(false);

  WeatherScheduler(WeatherScheduleUseCase schedule) {
    this.schedule = schedule;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.addTriggerTask(
        this::tick,
        ctx -> {
          Instant last = ctx.lastCompletion();
          try {
            return schedule.nextRunAt(last);
          } catch (RuntimeException e) {
            // DB를 잠깐 못 읽어도 스케줄이 멈추지 않게 1분 뒤 다시 본다
            log.warn("날씨 다음 실행 시각을 정하지 못해 1분 뒤 다시 봐요", e);
            return Instant.now().plusSeconds(60);
          }
        });
  }

  void tick() {
    boolean startup = started.compareAndSet(false, true);
    try {
      schedule.runScheduled(startup);
    } catch (RuntimeException e) {
      log.error("날씨 주기 실행 실패", e);
    }
  }

  /** 매일 04:00(한국 시간) 보관 기간이 지난 실행·호출 이력을 지운다 */
  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
  void purge() {
    try {
      schedule.purgeOldLogs();
    } catch (RuntimeException e) {
      log.error("날씨 이력 정리 실패", e);
    }
  }
}
