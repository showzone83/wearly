package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * WEARLY_AI_PROVIDER 규칙 (doc/07_관리자_사이트.md 3장): mock이면 목업, 그 밖(기본 db)은 DB 공급자.
 * 키가 없어도 시작은 한다(시작 WARN과 502는 AiProviderRoutingIntegrationTest). 모르는 값만 시작 실패.
 */
class AiModeTest {

    @Test
    void 기본값은_db다() throws Exception {
        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();
        assertThat(yml.getProperty("wearly.ai.provider")).hasToString("${WEARLY_AI_PROVIDER:db}");
        assertThat(yml.getProperty("wearly.ai.api-key")).isNull(); // 키는 공급자 행의 api_key_env로 읽는다
    }

    @Test
    void mock은_목업_그_밖은_DB() {
        assertThat(AiMode.parse(" MOCK ")).isEqualTo(AiMode.MOCK);
        assertThat(AiMode.parse("db")).isEqualTo(AiMode.DB);
        assertThat(AiMode.parse("")).isEqualTo(AiMode.DB);
        assertThat(AiMode.parse(null)).isEqualTo(AiMode.DB);
        assertThat(AiMode.parse("claude")).isEqualTo(AiMode.DB); // 예전 기본값
    }

    @Test
    void auto나_모르는_값은_시작_실패와_안내() {
        assertStartupFailure("auto", "auto는 더 이상 지원하지 않습니다");
        assertStartupFailure("openai", "db(기본) 또는 mock", "openai");
    }

    private static void assertStartupFailure(String value, String... parts) {
        assertThatThrownBy(() -> AiMode.parse(value)).isInstanceOf(AiProviderConfigException.class)
                .satisfies(e -> {
                    for (String p : parts) {
                        assertThat(e).hasMessageContaining(p);
                    }
                    FailureAnalysis analysis = new AiProviderFailureAnalyzer().analyze(e);
                    assertThat(analysis.getDescription()).startsWith("AI 공급자 설정 오류: ");
                    assertThat(analysis.getAction()).contains("./gradlew bootRunMock");
                });
    }
}
