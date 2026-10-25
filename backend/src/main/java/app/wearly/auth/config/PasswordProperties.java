package app.wearly.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비밀번호 변경 주기 설정 (wearly.auth.password.*, application.yml, 28 문서). 값이 틀리면 서버가 시작하지 않는다.
 *
 * <pre>
 * wearly.auth.password.change-interval    비밀번호를 바꾼 뒤 변경을 알리기까지의 기간 (기본 90일, 0이면 기능 끔)
 * wearly.auth.password.reminder-cron      기한이 지난 회원에게 푸시를 보내는 예약 작업 시각 (한국 시간, 기본 매일 10:00)
 * wearly.auth.password.reminder-batch     예약 작업 한 번에 보내는 최대 회원 수 (기본 500, 남으면 다음 날)
 * wearly.auth.password.reminder-enabled   false면 예약 작업을 만들지 않는다 (테스트용, 기본 true)
 * </pre>
 */
@ConfigurationProperties("wearly.auth.password")
public record PasswordProperties(
    @DefaultValue("P90D") Duration changeInterval,
    @DefaultValue("0 0 10 * * *") String reminderCron,
    @DefaultValue("500") int reminderBatch,
    @DefaultValue("true") boolean reminderEnabled) {}
