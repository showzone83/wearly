package app.wearly.image.adapter.in.scheduler;

import app.wearly.image.application.port.in.ImageCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 사진 정리 주기 작업 (wearly.storage.cleanup-interval, 기본 1시간. 시작 5분 뒤 처음).
 * wearly.storage.cleanup-enabled=false면 만들지 않는다 (테스트는 유스케이스를 직접 부른다)
 */
@Component
@ConditionalOnProperty(
    name = "wearly.storage.cleanup-enabled",
    havingValue = "true",
    matchIfMissing = true)
class ImageCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(ImageCleanupScheduler.class);

  private final ImageCleanupUseCase cleanup;

  ImageCleanupScheduler(ImageCleanupUseCase cleanup) {
    this.cleanup = cleanup;
  }

  @Scheduled(
      initialDelayString = "PT5M",
      fixedDelayString = "${wearly.storage.cleanup-interval:PT1H}")
  void run() {
    try {
      cleanup.cleanup();
    } catch (RuntimeException e) {
      log.warn("사진 정리 작업 실패 (다음 주기에 다시)", e);
    }
  }
}
