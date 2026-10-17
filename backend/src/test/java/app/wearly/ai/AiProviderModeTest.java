package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.application.service.AiProviderRegistry;
import app.wearly.ai.domain.AiProviderConfigException;
import app.wearly.common.config.AiProviderFailureAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** WEARLY_AI_PROVIDER: mock → 목업, db(기본)·claude(호환) → DB 공급자, 그 밖은 시작 실패 (07 문서 3장) */
class AiProviderModeTest {

  @Test
  void 기본값은_db다() throws Exception {
    PropertySource<?> yml =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();
    assertThat(yml.getProperty("wearly.ai.provider")).hasToString("${WEARLY_AI_PROVIDER:db}");
    assertThat(yml.getProperty("wearly.ai.allow-base-url"))
        .hasToString("${WEARLY_AI_ALLOW_BASE_URL:false}");
  }

  @Test
  void mock만_목업이고_db와_claude와_빈값은_DB_공급자() {
    assertThat(AiProviderRegistry.parseMode("mock")).isTrue();
    assertThat(AiProviderRegistry.parseMode(" MOCK ")).isTrue();
    assertThat(AiProviderRegistry.parseMode("db")).isFalse();
    assertThat(AiProviderRegistry.parseMode("claude")).isFalse();
    assertThat(AiProviderRegistry.parseMode("")).isFalse();
    assertThat(AiProviderRegistry.parseMode(null)).isFalse();
  }

  @Test
  void auto와_모르는_값은_시작_실패와_안내() {
    assertThatThrownBy(() -> AiProviderRegistry.parseMode("auto"))
        .isInstanceOf(AiProviderConfigException.class)
        .hasMessageContaining("auto는 더 이상 지원하지 않습니다");
    assertThatThrownBy(() -> AiProviderRegistry.parseMode("openai"))
        .isInstanceOf(AiProviderConfigException.class)
        .hasMessageContaining("db(기본");
    FailureAnalysis analysis =
        new AiProviderFailureAnalyzer()
            .analyze(new IllegalStateException(new AiProviderConfigException("x")));
    assertThat(analysis).isNotNull();
    assertThat(analysis.getDescription()).startsWith("AI 공급자 설정 오류: ");
    assertThat(analysis.getAction()).contains("./gradlew bootRunMock");
  }
}
