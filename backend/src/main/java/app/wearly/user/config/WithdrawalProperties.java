package app.wearly.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 탈퇴 유예 설정 (wearly.user.withdrawal.*, application.yml, doc/27). 값이 틀리면 서버가 시작하지 않는다.
 *
 * <pre>
 * wearly.user.withdrawal.grace   탈퇴 신청 뒤 회원을 지우기까지 기다리는 시간 (기본 30일)
 * </pre>
 */
@ConfigurationProperties("wearly.user.withdrawal")
public record WithdrawalProperties(@DefaultValue("P30D") Duration grace) {}
