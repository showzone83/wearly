package app.wearly.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.RejectedOutputException;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 공급자 폴백 루프와 성공·실패 기록 (가짜 포트, Spring 없이) */
class AiCallRunnerTest {

  static final AiJsonPrompt PROMPT =
      new AiJsonPrompt("plan", null, "프롬프트", Map.of("type", "object"), Map::of);
  static final AiCallRunner.Call CALL = new AiCallRunner.Call(7L, "OUTFIT_RECOMMEND", PROMPT, null);

  final List<AiCallRecord> records = new ArrayList<>();
  final AiCallRunner runner = new AiCallRunner(records::add);

  /** "ok" 키가 있어야 받아들이고, 값은 ok 문자열 */
  final JsonOutputHandler<String> handler =
      json -> {
        if (!json.containsKey("ok")) {
          throw new RejectedOutputException("ok가 없어요.");
        }
        return new JsonOutputHandler.Accepted<>((String) json.get("ok"), Map.of("k", "v"), "요약");
      };

  @Test
  void 실패한_공급자는_기록하고_다음_공급자로_넘어간다() {
    Optional<AiCallRunner.Attempt<String>> result =
        runner.first(
            List.of(
                provider(
                    "a",
                    () -> {
                      throw new AiProviderException("TIMEOUT", "시간 초과");
                    }),
                provider("b", () -> new AiJsonResult(Map.of("nope", 1), "b-1", 10L, 2L)),
                provider(
                    "c",
                    () -> {
                      throw new IllegalStateException("버그");
                    }),
                provider("d", () -> new AiJsonResult(Map.of("ok", "좋아요"), "d-real", 30L, 4L)),
                provider("e", () -> new AiJsonResult(Map.of("ok", "안 불림"), "e", 1L, 1L))),
            CALL,
            handler);

    assertThat(result)
        .hasValueSatisfying(
            r -> {
              assertThat(r.provider()).isEqualTo("d");
              assertThat(r.value()).isEqualTo("좋아요");
              assertThat(r.model()).isEqualTo("d-real");
            });
    assertThat(records)
        .extracting(AiCallRecord::provider, AiCallRecord::status, AiCallRecord::errorCode)
        .containsExactly(
            tuple("a", AiCallRecord.FAILED, "TIMEOUT"),
            tuple("b", AiCallRecord.FAILED, "REJECTED_OUTPUT"),
            tuple("c", AiCallRecord.FAILED, "UNEXPECTED"),
            tuple("d", AiCallRecord.SUCCESS, null));
    // 거부한 응답도 받은 모델·토큰을 남기고, 성공 기록에는 handler가 준 기록용 결과를 남긴다
    assertThat(records.get(1).model()).isEqualTo("b-1");
    assertThat(records.get(1).inputTokens()).isEqualTo(10L);
    assertThat(records.get(3).result()).isEqualTo(Map.of("k", "v"));
    assertThat(records.get(3).summary()).isEqualTo("요약");
    assertThat(records.get(3).inputTokens()).isEqualTo(30L);
    assertThat(records).allSatisfy(r -> assertThat(r.userId()).isEqualTo(7L));
  }

  @Test
  void 모두_실패하면_빈_값() {
    Optional<AiCallRunner.Attempt<String>> result =
        runner.first(
            List.of(provider("a", () -> new AiJsonResult(Map.of(), "a", null, null))),
            CALL,
            handler);

    assertThat(result).isEmpty();
    assertThat(records).singleElement().extracting(AiCallRecord::status).isEqualTo("FAILED");
  }

  @Test
  void 사진_호출의_실패는_설정된_모델과_사진_해시만_남긴다() {
    AiImage image = new AiImage(new byte[] {1, 2, 3}, "image/png", "abc123");
    AiCallRunner.Attempt<String> a =
        runner.attempt(
            provider("a", () -> new AiJsonResult(Map.of("nope", 1), "real", 5L, 5L)),
            new AiCallRunner.Call(7L, AiCallRecord.FEATURE_ITEM_ANALYZE, PROMPT, image),
            handler);

    assertThat(a.ok()).isFalse();
    assertThat(records)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.model()).isEqualTo("a-configured");
              assertThat(r.inputTokens()).isNull();
              assertThat(r.imageSha256()).isEqualTo(image.sha256());
              assertThat(r.imageBytes()).isEqualTo(3);
            });
  }

  private static AiModelPort provider(String name, Supplier<AiJsonResult> answer) {
    return new AiModelPort() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String model() {
        return name + "-configured";
      }

      @Override
      public AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image) {
        return answer.get();
      }
    };
  }
}
