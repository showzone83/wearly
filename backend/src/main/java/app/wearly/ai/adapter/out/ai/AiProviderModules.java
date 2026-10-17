package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.adapter.out.ai.claude.ClaudeModule;
import app.wearly.ai.adapter.out.ai.gemini.GeminiModule;
import app.wearly.ai.adapter.out.ai.openai.OpenAiModule;
import app.wearly.ai.domain.AiProviderType;
import org.springframework.stereotype.Component;

/** 공급자 종류 → 모듈. 종류를 고르는 곳은 이 switch 하나뿐이다 (default가 없어 새 종류를 빠뜨리면 컴파일 오류, M2) */
@Component
public class AiProviderModules {

  private final ClaudeModule claude;
  private final OpenAiModule openai;
  private final GeminiModule gemini;

  public AiProviderModules(ClaudeModule claude, OpenAiModule openai, GeminiModule gemini) {
    this.claude = claude;
    this.openai = openai;
    this.gemini = gemini;
  }

  public AiProviderModule of(AiProviderType type) {
    return switch (type) {
      case CLAUDE -> claude;
      case OPENAI -> openai;
      case GEMINI -> gemini;
    };
  }
}
