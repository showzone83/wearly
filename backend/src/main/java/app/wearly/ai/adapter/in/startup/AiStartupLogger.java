package app.wearly.ai.adapter.in.startup;

import app.wearly.ai.application.port.in.AiStatusUseCase;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서버가 요청을 받기 시작할 때 AI 공급자 상태를 로그로 남긴다 */
@Component
class AiStartupLogger {

  private final AiStatusUseCase status;

  AiStartupLogger(AiStatusUseCase status) {
    this.status = status;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    status.logStartupStatus();
  }
}
