package app.wearly.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 공급자 선택 순서와 입력 규칙 (07 문서 3장, Spring 없이) */
class AiProviderSelectionTest {

  static AiProviderSetting p(String id, int priority, boolean enabled, String model) {
    return new AiProviderSetting(
        id, AiProviderType.OPENAI, id, model, "X_API_KEY", enabled, priority, Map.of(), null);
  }

  @Test
  void 사용_모델_키를_모두_갖춘_공급자만_priority_순으로_고른다() {
    List<AiProviderSetting> all =
        List.of(
            p("a", 5, true, "m"),
            p("b", 1, true, "m"),
            p("off", 0, false, "m"),
            p("nomodel", 0, true, " "),
            p("nokey", 0, true, "m"),
            p("c", 1, true, "m"));
    Set<String> withKey = Set.of("a", "b", "off", "nomodel", "c");
    List<String> order =
        AiProviderSelection.candidates(all, s -> withKey.contains(s.providerId())).stream()
            .map(AiProviderSetting::providerId)
            .toList();
    // priority가 같으면(b·c) 받은 순서(저장소의 provider_id 순)를 지킨다
    assertThat(order).containsExactly("b", "c", "a");
    assertThat(AiProviderSelection.usable(p("x", 1, true, "m"), false)).isFalse();
    assertThat(AiProviderSelection.usable(p("x", 1, true, null), true)).isFalse();
  }

  @Test
  void WEARLY_AI_PROVIDER_해석() {
    assertThat(AiRunMode.isMock("mock")).isTrue();
    assertThat(AiRunMode.isMock(" MOCK ")).isTrue();
    assertThat(AiRunMode.isMock("db")).isFalse();
    assertThat(AiRunMode.isMock("claude")).isFalse();
    assertThat(AiRunMode.isMock(null)).isFalse();
    assertThatThrownBy(() -> AiRunMode.isMock("auto"))
        .isInstanceOf(AiProviderConfigException.class);
    assertThatThrownBy(() -> AiRunMode.isMock("gemini"))
        .isInstanceOf(AiProviderConfigException.class);
  }

  @Test
  void 키_환경변수_이름은_API_KEY_형식만_허용한다() {
    assertThat(AiProviderRules.validEnvName("ANTHROPIC_API_KEY")).isTrue();
    assertThat(AiProviderRules.validEnvName("WEARLY_JWT_SECRET")).isFalse();
    assertThat(AiProviderRules.validEnvName("SPRING_DATASOURCE_PASSWORD")).isFalse();
    assertThat(AiProviderRules.validEnvName("lower_API_KEY")).isFalse();
    assertThat(AiProviderRules.validEnvName(null)).isFalse();
    List<FieldError> errors = new ArrayList<>();
    AiProviderRules.providerId("mock", errors);
    AiProviderRules.providerId("Bad Id", errors);
    AiProviderRules.model("bad model!", errors);
    AiProviderRules.type("MOCK", errors);
    assertThat(errors)
        .extracting(FieldError::field)
        .containsExactly("providerId", "providerId", "model", "type");
    assertThat(AiProviderRules.model("  ", errors)).isNull();
  }

  @Test
  void 공급자_options_검증() {
    assertThat(AiProviderType.CLAUDE.validate(Map.of("effort", "high", "maxTokens", 1000), false))
        .isEmpty();
    assertThat(AiProviderType.CLAUDE.validate(Map.of("baseUrl", "http://x"), false))
        .extracting(FieldError::field)
        .containsExactly("options.baseUrl");
    assertThat(AiProviderType.OPENAI.validate(Map.of("temperature", 3, "nope", 1), true))
        .extracting(FieldError::field)
        .containsExactlyInAnyOrder("options.temperature", "options.nope");
  }

  @Test
  void 호출_기록의_오류_코드는_50자로_자른다() {
    AiImage image = new AiImage(new byte[] {1, 2}, "image/jpeg", "s".repeat(64));
    AiCallRecord r =
        AiCallRecord.failure(
            1L, AiCallRecord.FEATURE_ITEM_ANALYZE, "p", "m", "E".repeat(80), 3, image);
    assertThat(r.errorCode()).hasSize(50);
    assertThat(r.status()).isEqualTo(AiCallRecord.FAILED);
    assertThat(r.imageBytes()).isEqualTo(2);
    assertThat(AiImages.sha256(new byte[0]))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
