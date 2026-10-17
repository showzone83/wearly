package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiLimitExceededException;
import app.wearly.ai.application.port.in.AiUnavailableException;
import app.wearly.ai.application.port.in.JsonGenerationUseCase;
import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.dto.GenerateJsonDto;
import app.wearly.ai.application.port.in.dto.GeneratedJsonDto;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiJsonPrompt;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 이미지 없는 구조화 JSON 생성: 공급자를 우선순위 순으로 시도 → handler 검증 → 기록(JPA). 공급자 오류도, handler가 거부한 응답도 실패로 기록하고
 * 다음 공급자로 넘어간다. 호출 전에 일일 한도를 예약하고 끝나면 지운다 (M7). 목업 실행이면 목업 공급자가 command.mockOutput을 돌려준다.
 */
@Service
public class AiJsonGenerationService implements JsonGenerationUseCase {

  private static final Logger log = LoggerFactory.getLogger(AiJsonGenerationService.class);

  private final AiProviderRegistry registry;
  private final AiQuotaService quota;
  private final AiCallRunner runner;

  public AiJsonGenerationService(
      AiProviderRegistry registry, AiQuotaService quota, AiCallRunner runner) {
    this.registry = registry;
    this.quota = quota;
    this.runner = runner;
  }

  @Override
  public <T> GeneratedJsonDto<T> generate(GenerateJsonDto command, JsonOutputHandler<T> handler) {
    AiQuotaService.Reservation reservation =
        quota
            .reserve(command.userId(), command.feature(), command.dailyLimit())
            .orElseThrow(() -> new AiLimitExceededException(command.feature()));
    try {
      List<AiModelPort> candidates = registry.candidates();
      if (candidates.isEmpty()) {
        log.warn("AI {} 불가: 쓸 수 있는 AI 공급자가 없음 (관리자 화면에서 공급자·모델·키를 확인)", command.feature());
        throw new AiUnavailableException(command.feature());
      }
      AiJsonPrompt prompt =
          new AiJsonPrompt(
              command.schemaName(),
              command.instructions(),
              command.prompt(),
              command.schema(),
              command.mockOutput());
      AiCallRunner.Attempt<T> ok =
          runner
              .first(
                  candidates,
                  new AiCallRunner.Call(command.userId(), command.feature(), prompt, null),
                  handler)
              .orElseThrow(() -> new AiUnavailableException(command.feature()));
      return new GeneratedJsonDto<>(ok.value(), ok.provider(), ok.model(), reservation.used());
    } finally {
      quota.release(reservation);
    }
  }
}
