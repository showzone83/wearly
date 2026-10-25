package app.wearly.pairing.adapter.in.schedule;

import app.wearly.pairing.application.port.in.ProxyDigestUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 옷장 도우미 묶음 알림 작업 (doc/26 3단계): wearly.pairing.notify.scan-interval(기본 5분)마다 보낼 때가 된 묶음 알림을 보낸다. 서버
 * 푸시가 꺼져 있거나(wearly.notify.push.enabled=false, 기본) wearly.pairing.notify.scheduler-enabled=false면
 * 만들지 않는다. 예약은 notify의 NotifyConfig가 켠다.
 */
@Component
@ConditionalOnExpression(
    "${wearly.notify.push.enabled:false} and ${wearly.pairing.notify.scheduler-enabled:true}")
class ProxyDigestScheduler {

  private static final Logger log = LoggerFactory.getLogger(ProxyDigestScheduler.class);

  private final ProxyDigestUseCase digest;

  ProxyDigestScheduler(ProxyDigestUseCase digest) {
    this.digest = digest;
  }

  @Scheduled(
      fixedDelayString = "${wearly.pairing.notify.scan-interval:PT5M}",
      initialDelayString = "${wearly.pairing.notify.scan-interval:PT5M}")
  void run() {
    try {
      digest.sendDue();
    } catch (RuntimeException e) {
      log.error("옷장 도우미 묶음 알림 작업 실패", e);
    }
  }
}
