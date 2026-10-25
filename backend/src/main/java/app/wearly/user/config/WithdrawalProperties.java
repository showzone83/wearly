package app.wearly.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 탈퇴 유예 설정 (wearly.user.withdrawal.*, application.yml, doc/27). 값이 틀리면 서버가 시작하지 않는다.
 *
 * <pre>
 * wearly.user.withdrawal.grace              탈퇴 신청 뒤 회원을 지우기까지 기다리는 시간 (기본 30일)
 * wearly.user.withdrawal.purge-cron         기한이 지난 회원을 지우는 예약 작업 시각 (한국 시간, 기본 매일 04:30)
 * wearly.user.withdrawal.purge-batch        예약 작업 한 번에 지우는 최대 회원 수 (기본 500, 남으면 다음 날)
 * wearly.user.withdrawal.scheduler-enabled  false면 예약 작업을 만들지 않는다 (테스트용, 기본 true)
 * </pre>
 */
@ConfigurationProperties("wearly.user.withdrawal")
public record WithdrawalProperties(
    @DefaultValue("P30D") Duration grace,
    @DefaultValue("0 30 4 * * *") String purgeCron,
    @DefaultValue("500") int purgeBatch,
    @DefaultValue("true") boolean schedulerEnabled) {

  public WithdrawalProperties {
    if (purgeBatch < 1) {
      throw new IllegalArgumentException(
          "wearly.user.withdrawal.purge-batch는 1 이상이어야 해요: " + purgeBatch);
    }
  }
}
