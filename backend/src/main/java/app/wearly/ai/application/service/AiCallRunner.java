package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.RejectedOutputException;
import app.wearly.ai.application.port.out.AiCallLogPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공급자 호출 한 번과 폴백 루프 (L7: 사진 분석·관리자 시험 분석·구조화 JSON 생성이 같이 쓴다). 호출 → handler 검증 → 기록(JPA)을 하고, 공급자
 * 오류(reason)·handler 거부(REJECTED_OUTPUT, 사진 분석의 규칙 위반은 INVALID_OUTPUT)·예상하지 못한 오류(UNEXPECTED)를 모두
 * 실패로 기록한다. 트랜잭션은 열지 않는다 (외부 호출 동안 DB 연결을 잡지 않는다).
 */
@Service
public class AiCallRunner {

  private static final Logger log = LoggerFactory.getLogger(AiCallRunner.class);

  private final AiCallLogPort logs;

  public AiCallRunner(AiCallLogPort logs) {
    this.logs = logs;
  }

  /**
   * 호출 한 건.
   *
   * @param image 사진 분석이면 사진, 아니면 null
   */
  public record Call(long userId, String feature, AiJsonPrompt prompt, AiImage image) {}

  /** 호출 결과. 성공이면 value·model·토큰, 실패면 errorCode·errorMessage */
  public record Attempt<T>(
      String provider,
      T value,
      String model,
      Long inputTokens,
      Long outputTokens,
      String errorCode,
      String errorMessage,
      int latencyMs) {

    public boolean ok() {
      return errorCode == null;
    }
  }

  /** 공급자를 차례로 불러 handler가 받아들인 첫 결과. 모두 실패하면 빈 값 */
  public <T> Optional<Attempt<T>> first(
      List<AiModelPort> candidates, Call call, JsonOutputHandler<T> handler) {
    for (AiModelPort provider : candidates) {
      Attempt<T> attempt = attempt(provider, call, handler);
      if (attempt.ok()) {
        return Optional.of(attempt);
      }
    }
    return Optional.empty();
  }

  /** 공급자 한 번: 호출 → 검증 → 기록 */
  public <T> Attempt<T> attempt(AiModelPort provider, Call call, JsonOutputHandler<T> handler) {
    long started = System.nanoTime();
    AiJsonResult received = null;
    String errorCode;
    String errorMessage;
    try {
      received = provider.generateJson(call.prompt(), call.image());
      JsonOutputHandler.Accepted<T> accepted = handler.accept(received.json());
      int ms = elapsedMs(started);
      logs.save(success(call, provider.name(), received, ms, accepted));
      // 토큰 한 줄 (입력은 캐시 읽기·쓰기 포함 전체). 공급자가 주지 않은 값은 null
      log.info(
          "AI {} 성공 provider={} model={} ms={} input={} output={} cacheRead={} cacheWrite={}",
          call.feature(),
          provider.name(),
          received.model(),
          ms,
          received.inputTokens(),
          received.outputTokens(),
          received.cacheReadTokens(),
          received.cacheWriteTokens());
      return new Attempt<>(
          provider.name(),
          accepted.value(),
          received.model(),
          received.inputTokens(),
          received.outputTokens(),
          null,
          null,
          ms);
    } catch (AiProviderException e) {
      log.warn(
          "AI {} 실패 provider={} reason={} message={}",
          call.feature(),
          provider.name(),
          e.reason(),
          e.getMessage());
      errorCode = e.reason();
      errorMessage = e.getMessage();
    } catch (RejectedOutputException e) {
      log.warn(
          "AI {} 응답 거부 provider={} message={}", call.feature(), provider.name(), e.getMessage());
      errorCode = RejectedOutputException.REASON;
      errorMessage = e.getMessage();
    } catch (RuntimeException e) {
      log.error(
          "AI {} 중 예상하지 못한 오류 provider={} type={}",
          call.feature(),
          provider.name(),
          e.getClass().getName());
      errorCode = "UNEXPECTED";
      errorMessage = "예상하지 못한 오류: " + e.getClass().getSimpleName();
    }
    int ms = elapsedMs(started);
    logs.save(failure(call, provider, errorCode, ms, received));
    return new Attempt<>(provider.name(), null, null, null, null, errorCode, errorMessage, ms);
  }

  private static AiCallRecord success(
      Call call, String provider, AiJsonResult r, int ms, JsonOutputHandler.Accepted<?> accepted) {
    return new AiCallRecord(
        call.userId(),
        call.feature(),
        provider,
        r.model(),
        AiCallRecord.SUCCESS,
        ms,
        r.inputTokens(),
        r.outputTokens(),
        call.image() == null ? null : call.image().sha256(),
        call.image() == null ? null : call.image().bytes().length,
        accepted.logResult(),
        accepted.summary(),
        null);
  }

  /** 사진 호출의 실패 기록은 설정된 모델만 남긴다 (예전과 같다). 이미지 없는 호출은 응답을 받았으면 그 모델·토큰을 남긴다 */
  private static AiCallRecord failure(
      Call call, AiModelPort provider, String errorCode, int ms, AiJsonResult received) {
    if (call.image() != null) {
      return AiCallRecord.failure(
          call.userId(),
          call.feature(),
          provider.name(),
          provider.model(),
          errorCode,
          ms,
          call.image());
    }
    return AiCallRecord.jsonFailure(
        call.userId(), call.feature(), provider.name(), provider.model(), errorCode, ms, received);
  }

  private static int elapsedMs(long startedNanos) {
    return (int) ((System.nanoTime() - startedNanos) / 1_000_000);
  }
}
