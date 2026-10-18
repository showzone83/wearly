package app.wearly.ai.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.common.config.WearlyProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** wearly.ai.prompt-cache.enabled → 공급자 옵션 (Claude 공급자가 cache_control을 붙일지) */
class ProviderOptionsTest {

  final AiProviderSetting row =
      new AiProviderSetting(
          "claude",
          AiProviderType.CLAUDE,
          "Claude",
          "m",
          "ANTHROPIC_API_KEY",
          true,
          1,
          Map.of(),
          null);

  @Test
  void 프롬프트_캐싱은_설정이_없으면_켜지고_false면_꺼진다() {
    assertThat(ProviderOptions.of(row, props(null)).promptCache()).isTrue();
    assertThat(ProviderOptions.of(row, props(new WearlyProperties.PromptCache(null))).promptCache())
        .isTrue();
    assertThat(ProviderOptions.of(row, props(new WearlyProperties.PromptCache(true))).promptCache())
        .isTrue();
    assertThat(
            ProviderOptions.of(row, props(new WearlyProperties.PromptCache(false))).promptCache())
        .isFalse();
  }

  private static WearlyProperties.Ai props(WearlyProperties.PromptCache cache) {
    return new WearlyProperties.Ai(
        "db", 10, 20, Duration.ofSeconds(60), false, null, null, null, cache);
  }
}
