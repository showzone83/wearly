package app.wearly.user.adapter.in.schedule;

import app.wearly.user.application.port.in.WithdrawalPurgeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 예약 삭제 작업 (doc/27 2단계): wearly.user.withdrawal.purge-cron(한국 시간, 기본 매일 04:30)마다 유예 기간이 지난 탈퇴 예정
 * 회원을 지운다. wearly.user.withdrawal.scheduler-enabled=false면 만들지 않는다 (테스트는 유스케이스를 직접 부른다). 예약은 user의
 * UserConfig가 켠다.
 */
@Component
@ConditionalOnProperty(
    name = "wearly.user.withdrawal.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
class WithdrawalPurgeScheduler {

  private static final Logger log = LoggerFactory.getLogger(WithdrawalPurgeScheduler.class);

  private final WithdrawalPurgeUseCase purge;

  WithdrawalPurgeScheduler(WithdrawalPurgeUseCase purge) {
    this.purge = purge;
  }

  @Scheduled(cron = "${wearly.user.withdrawal.purge-cron:0 30 4 * * *}", zone = "Asia/Seoul")
  void run() {
    try {
      purge.purgeDue();
    } catch (RuntimeException e) {
      log.error("탈퇴 예약 삭제 작업 실패 (다음 예약 때 다시)", e);
    }
  }
}
