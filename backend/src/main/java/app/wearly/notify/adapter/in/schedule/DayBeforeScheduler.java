package app.wearly.notify.adapter.in.schedule;

import app.wearly.common.domain.Zones;
import app.wearly.notify.application.port.in.DayBeforeNotifyUseCase;
import app.wearly.notify.domain.PushSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * D-1 알림 작업: 매일 wearly.notify.day-before.time(기본 19:30, 한국 시간)에 내일 예정의 알림을 보낸다. 서버 푸시가 꺼져 있거나
 * (wearly.notify.push.enabled=false, 기본) scheduler-enabled=false면 만들지 않는다.
 */
@Component
@ConditionalOnExpression(
    "${wearly.notify.push.enabled:false} and ${wearly.notify.day-before.scheduler-enabled:true}")
class DayBeforeScheduler implements SchedulingConfigurer {

  private static final Logger log = LoggerFactory.getLogger(DayBeforeScheduler.class);

  private final DayBeforeNotifyUseCase dayBefore;
  private final PushSettings settings;

  DayBeforeScheduler(DayBeforeNotifyUseCase dayBefore, PushSettings settings) {
    this.dayBefore = dayBefore;
    this.settings = settings;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    String cron =
        "0 "
            + settings.dayBeforeTime().getMinute()
            + " "
            + settings.dayBeforeTime().getHour()
            + " * * *";
    registrar.addTriggerTask(this::run, new CronTrigger(cron, Zones.SEOUL));
    log.info("D-1 알림 작업을 매일 {} (한국 시간)에 실행해요", settings.dayBeforeTime());
  }

  void run() {
    try {
      dayBefore.runDayBefore();
    } catch (RuntimeException e) {
      log.error("D-1 알림 작업 실패", e);
    }
  }
}
