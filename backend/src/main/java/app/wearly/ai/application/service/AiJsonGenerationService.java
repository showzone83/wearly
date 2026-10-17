package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.JsonGenerationUseCase;
import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.RejectedOutputException;
import app.wearly.ai.application.port.in.dto.GenerateJsonDto;
import app.wearly.ai.application.port.in.dto.GeneratedJsonDto;
import app.wearly.ai.application.port.out.AiCallLogPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.application.port.out.AiUsagePort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 이미지 없는 구조화 JSON 생성: 공급자를 우선순위 순으로 시도 → handler 검증 → 기록(JPA). 공급자 오류도, handler가 거부한 응답도 실패로 기록하고
 * 다음 공급자로 넘어간다. 목업 실행이면 목업 공급자가 command.mockOutput을 돌려준다.
 */
@Service
public class AiJsonGenerationService implements JsonGenerationUseCase {

  private static final Logger log = LoggerFactory.getLogger(AiJsonGenerationService.class);

  private final AiProviderRegistry registry;
  private final AiUsagePort usage;
  private final AiCallLogPort logs;
  private final Clock clock;

  public AiJsonGenerationService(
      AiProviderRegistry registry, AiUsagePort usage, AiCallLogPort logs, Clock clock) {
    this.registry = registry;
    this.usage = usage;
    this.logs = logs;
    this.clock = clock;
  }

  @Override
  public <T> GeneratedJsonDto<T> generate(GenerateJsonDto command, JsonOutputHandler<T> handler) {
    List<AiModelPort> candidates = registry.candidates();
    if (candidates.isEmpty()) {
      log.warn("AI {} 불가: 쓸 수 있는 AI 공급자가 없음 (관리자 화면에서 공급자·모델·키를 확인)", command.feature());
      throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }
    AiJsonPrompt prompt =
        new AiJsonPrompt(
            command.schemaName(),
            command.instructions(),
            command.prompt(),
            command.schema(),
            command.mockOutput());
    for (AiModelPort provider : candidates) {
      long started = System.nanoTime();
      AiJsonResult received = null;
      String errorCode;
      try {
        received = provider.generateJson(prompt);
        JsonOutputHandler.Accepted<T> accepted = handler.accept(received.json());
        logs.save(
            AiCallRecord.jsonSuccess(
                command.userId(),
                command.feature(),
                provider.name(),
                received,
                elapsedMs(started),
                accepted.logResult()));
        return new GeneratedJsonDto<>(accepted.value(), provider.name(), received.model());
      } catch (AiProviderException e) {
        log.warn(
            "AI {} 실패 provider={} reason={} message={}",
            command.feature(),
            provider.name(),
            e.reason(),
            e.getMessage());
        errorCode = e.reason();
      } catch (RejectedOutputException e) {
        log.warn(
            "AI {} 응답 거부 provider={} message={}",
            command.feature(),
            provider.name(),
            e.getMessage());
        errorCode = RejectedOutputException.REASON;
      } catch (RuntimeException e) {
        log.error(
            "AI {} 중 예상하지 못한 오류 provider={} type={}",
            command.feature(),
            provider.name(),
            e.getClass().getName());
        errorCode = "UNEXPECTED";
      }
      logs.save(
          AiCallRecord.jsonFailure(
              command.userId(),
              command.feature(),
              provider.name(),
              provider.model(),
              errorCode,
              elapsedMs(started),
              received));
    }
    throw new ApiException(ErrorCode.AI_UNAVAILABLE);
  }

  @Override
  public long successToday(long userId, String feature) {
    LocalDate today = Zones.today(clock);
    return usage.countSuccess(
        userId, feature, Zones.startOf(today), Zones.startOf(today.plusDays(1)));
  }

  private static int elapsedMs(long startedNanos) {
    return (int) ((System.nanoTime() - startedNanos) / 1_000_000);
  }
}
