package app.wearly.pairing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 옷장 도우미 설정 (wearly.pairing.*, application.yml, doc/26). 값이 틀리면 서버가 시작하지 않는다.
 *
 * <pre>
 * wearly.pairing.max-helpers-per-owner   주인 한 명의 돕는 사람 수 (기본 1, 대기 포함)
 * wearly.pairing.max-closets-per-helper  돕는 사람 한 명이 맡는 옷장 수 (기본 2, 대기 포함)
 * wearly.pairing.code-ttl                연결 코드 유효 시간 (기본 24시간)
 * wearly.pairing.pending-ttl             주인 승인 대기 시간 (기본 7일)
 * wearly.pairing.join-attempt-limit      코드 입력 시도 한도 (기본 10)
 * wearly.pairing.join-attempt-window     시도를 세는 시간 (기본 1시간)
 * </pre>
 */
@ConfigurationProperties("wearly.pairing")
public record PairingProperties(
    @DefaultValue("1") int maxHelpersPerOwner,
    @DefaultValue("2") int maxClosetsPerHelper,
    @DefaultValue("PT24H") Duration codeTtl,
    @DefaultValue("P7D") Duration pendingTtl,
    @DefaultValue("10") int joinAttemptLimit,
    @DefaultValue("PT1H") Duration joinAttemptWindow) {

  public PairingProperties {
    if (maxHelpersPerOwner < 1 || maxClosetsPerHelper < 1 || joinAttemptLimit < 1) {
      throw new IllegalArgumentException(
          "wearly.pairing의 인원·시도 한도는 1 이상이어야 한다: "
              + maxHelpersPerOwner
              + ", "
              + maxClosetsPerHelper
              + ", "
              + joinAttemptLimit);
    }
    if (codeTtl.isNegative()
        || codeTtl.isZero()
        || pendingTtl.isNegative()
        || pendingTtl.isZero()
        || joinAttemptWindow.isNegative()
        || joinAttemptWindow.isZero()) {
      throw new IllegalArgumentException("wearly.pairing의 시간 값은 0보다 커야 한다");
    }
  }
}
