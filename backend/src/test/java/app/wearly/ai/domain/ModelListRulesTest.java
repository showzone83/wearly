package app.wearly.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 모델 목록 규칙: OpenAI·Gemini 거르기, 내장 목록 추천 표시, 정렬, 키 지문 (Spring 없이) */
class ModelListRulesTest {

  @Test
  void OpenAI는_gpt와_o_추론_계열만_남기고_채팅_이미지_입력이_아닌_것은_뺀다() {
    for (String id :
        List.of("gpt-5.1", "gpt-5.1-mini", "gpt-4o", "gpt-4.1-nano", "o3", "o4-mini", "o1-pro")) {
      assertThat(ModelListRules.isOpenAiChatModel(id)).as(id).isTrue();
    }
    for (String id :
        List.of(
            "text-embedding-3-large",
            "tts-1-hd",
            "gpt-4o-mini-tts",
            "whisper-1",
            "dall-e-3",
            "omni-moderation-latest",
            "gpt-4o-realtime-preview",
            "gpt-4o-audio-preview",
            "gpt-4o-transcribe",
            "gpt-4o-search-preview",
            "gpt-image-1",
            "gpt-3.5-turbo",
            "gpt-3.5-turbo-instruct",
            "gpt-4",
            "gpt-4-0613",
            "davinci-002",
            "babbage-002",
            "computer-use-preview",
            "o3-deep-research",
            "codex-mini-latest",
            "",
            "  ")) {
      assertThat(ModelListRules.isOpenAiChatModel(id)).as(id).isFalse();
    }
    assertThat(ModelListRules.isOpenAiChatModel(null)).isFalse();
    // 대소문자는 가리지 않는다
    assertThat(ModelListRules.isOpenAiChatModel("GPT-5.1")).isTrue();
  }

  @Test
  void Gemini는_generateContent가_되는_것만_models_접두사를_떼고_남긴다() {
    List<String> generate = List.of("generateContent", "countTokens");
    assertThat(ModelListRules.geminiModelId("models/gemini-2.5-flash", generate))
        .isEqualTo("gemini-2.5-flash");
    assertThat(ModelListRules.geminiModelId("gemini-2.5-pro", generate))
        .isEqualTo("gemini-2.5-pro");
    // generateContent가 없음
    assertThat(
            ModelListRules.geminiModelId(
                "models/gemini-2.0-flash-live-001", List.of("bidiGenerateContent")))
        .isNull();
    assertThat(ModelListRules.geminiModelId("models/text-embedding-004", List.of("embedContent")))
        .isNull();
    // generateContent가 있어도 사진 분석에 맞지 않는 계열
    for (String name :
        List.of(
            "models/gemini-embedding-exp",
            "models/aqa",
            "models/imagen-3.0-generate-002",
            "models/veo-3.0-generate-preview",
            "models/gemini-2.5-flash-preview-tts",
            "models/gemini-2.5-flash-native-audio-dialog",
            "models/gemini-2.5-flash-image",
            "models/gemma-3-27b-it")) {
      assertThat(ModelListRules.geminiModelId(name, generate)).as(name).isNull();
    }
    assertThat(ModelListRules.geminiModelId(null, generate)).isNull();
    assertThat(ModelListRules.geminiModelId("models/", generate)).isNull();
    assertThat(ModelListRules.geminiModelId("models/gemini-2.5-flash", null)).isNull();
  }

  @Test
  void 제공사_목록에_내장_목록의_추천과_이름을_붙이고_추천_최신_ID_순으로_정렬한다() {
    List<AiModelEntry> catalog =
        List.of(
            new AiModelEntry("claude-opus-5-5", "Claude Opus 5.5", null, false),
            new AiModelEntry("claude-sonnet-5-5", "Claude Sonnet 5.5", null, true),
            new AiModelEntry("claude-not-live", "없는 모델", null, true));
    List<AiModelEntry> live =
        List.of(
            AiModelEntry.live("b-model", null, null),
            AiModelEntry.live("claude-opus-5-5", "Opus (제공사 이름)", t("2026-08-01")),
            AiModelEntry.live("claude-sonnet-5-5", "claude-sonnet-5-5", t("2026-01-01")),
            AiModelEntry.live("a-model", "A", null),
            AiModelEntry.live("newest", "Newest", t("2026-10-01")),
            AiModelEntry.live("newest", "중복", t("2020-01-01")));

    List<AiModelEntry> out = ModelListRules.withCatalog(live, catalog);

    assertThat(out)
        .extracting(AiModelEntry::id)
        .containsExactly("claude-sonnet-5-5", "newest", "claude-opus-5-5", "a-model", "b-model");
    assertThat(out.get(0).recommended()).isTrue();
    // 제공사가 이름을 주지 않았으면(=ID) 내장 목록 이름, 주었으면 제공사 이름
    assertThat(out.get(0).displayName()).isEqualTo("Claude Sonnet 5.5");
    assertThat(out.get(2).displayName()).isEqualTo("Opus (제공사 이름)");
    assertThat(out.get(2).recommended()).isFalse();
    assertThat(out.get(1).displayName()).isEqualTo("Newest"); // 같은 ID는 처음 것
    assertThat(out.get(4).displayName()).isEqualTo("b-model"); // 이름이 없으면 ID
    // 내장 목록에만 있는 모델은 제공사 목록에 넣지 않는다
    assertThat(out).extracting(AiModelEntry::id).doesNotContain("claude-not-live");
  }

  @Test
  void 내장_목록만_정렬하면_추천_먼저_그다음_ID_순() {
    List<AiModelEntry> sorted =
        ModelListRules.sorted(
            List.of(
                new AiModelEntry("claude-opus-5-5", "Opus", null, false),
                new AiModelEntry("claude-sonnet-5-5", "Sonnet", null, true),
                new AiModelEntry("claude-haiku-5-5", "Haiku", null, false)));
    assertThat(sorted)
        .extracting(AiModelEntry::id)
        .containsExactly("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5");
  }

  @Test
  void 키_지문은_키마다_다르고_키_값을_담지_않는다() {
    String a = ApiKeyRules.fingerprint("sk-test-key-aaaaaaaaaaaa");
    assertThat(a).hasSize(16).matches("[0-9a-f]{16}");
    assertThat(a).isEqualTo(ApiKeyRules.fingerprint("sk-test-key-aaaaaaaaaaaa"));
    assertThat(a).isNotEqualTo(ApiKeyRules.fingerprint("sk-test-key-bbbbbbbbbbbb"));
    assertThat(a).doesNotContain("aaaa");
  }

  private static Instant t(String date) {
    return Instant.parse(date + "T00:00:00Z");
  }
}
