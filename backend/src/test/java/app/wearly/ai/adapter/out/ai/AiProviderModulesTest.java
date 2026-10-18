package app.wearly.ai.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.adapter.out.ai.claude.ClaudeModule;
import app.wearly.ai.adapter.out.ai.gemini.GeminiModule;
import app.wearly.ai.adapter.out.ai.openai.OpenAiModule;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** M2: 공급자 종류마다 모듈이 하나씩 있고, 종류 오류 문구는 종류 목록에서 만든다 (Spring 없이) */
class AiProviderModulesTest {

  final JsonMapper json = JsonMapper.builder().build();
  final AiProviderModules modules =
      new AiProviderModules(new ClaudeModule(json), new OpenAiModule(json), new GeminiModule(json));

  @Test
  void 모든_공급자_종류에_자기_모듈이_있다() {
    for (AiProviderType t : AiProviderType.values()) {
      assertThat(modules.of(t).type()).isEqualTo(t);
    }
  }

  @Test
  void 종류_오류의_허용_목록은_종류_상수에서_만든다() {
    List<FieldError> errors = new ArrayList<>();
    assertThat(AiProviderRules.type("MISTRAL", errors)).isNull();
    assertThat(errors)
        .containsExactly(FieldError.notAllowed("type", List.of("CLAUDE", "OPENAI", "GEMINI")));
    assertThat(AiProviderRules.type(" GEMINI ", errors)).isEqualTo(AiProviderType.GEMINI);
  }
}
