package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.config.WearlyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** WEARLY_AI_PROVIDER 선택 규칙: 기본 claude, 키 없으면 시작 실패, mock은 명시할 때만, auto는 시작 실패. */
class AiProviderConfigTest {

    /** application.yml·.env를 읽지 않고 필요한 값만 직접 준다 (개발자 PC의 .env·환경변수에 흔들리지 않게). */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "wearly.jwt.issuer=wearly",
                    "wearly.jwt.secret=test-only-wearly-jwt-secret-0123456789abcdef",
                    "wearly.jwt.access-ttl=PT30M",
                    "wearly.jwt.refresh-ttl=P30D",
                    "wearly.ai.model=claude-opus-5-5",
                    "wearly.ai.daily-limit=10",
                    "wearly.ai.effort=medium",
                    "wearly.ai.timeout=PT60S",
                    "wearly.cors.allowed-origins=http://localhost:8081");

    @Test
    void 기본값은_claude다() throws Exception {
        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();
        assertThat(yml.getProperty("wearly.ai.provider")).hasToString("${WEARLY_AI_PROVIDER:claude}");
    }

    @Test
    void claude인데_키가_없으면_시작_실패() {
        runner.withPropertyValues("wearly.ai.provider=claude", "wearly.ai.api-key=")
                .run(context -> assertStartupFails(context, "ANTHROPIC_API_KEY가 없습니다", "bootRunMock"));
    }

    @Test
    void claude인데_키가_공백뿐이면_시작_실패() {
        runner.withPropertyValues("wearly.ai.provider=claude", "wearly.ai.api-key=   ")
                .run(context -> assertStartupFails(context, "ANTHROPIC_API_KEY가 없습니다"));
    }

    @Test
    void auto는_시작_실패() {
        runner.withPropertyValues("wearly.ai.provider=auto", "wearly.ai.api-key=sk-ant-test")
                .run(context -> assertStartupFails(context, "auto는 더 이상 지원하지 않습니다", "mock"));
    }

    @Test
    void 모르는_값은_시작_실패() {
        runner.withPropertyValues("wearly.ai.provider=openai", "wearly.ai.api-key=sk-ant-test")
                .run(context -> assertStartupFails(context, "claude(기본) 또는 mock", "openai"));
    }

    @Test
    void claude에_키가_있으면_Claude_공급자() {
        runner.withPropertyValues("wearly.ai.provider=claude", "wearly.ai.api-key=sk-ant-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiProvider provider = context.getBean(AiProvider.class);
                    assertThat(provider).isInstanceOf(ClaudeAiProvider.class);
                    assertThat(provider.name()).isEqualTo("claude");
                    assertThat(provider.model()).isEqualTo("claude-opus-5-5");
                });
    }

    @Test
    void mock은_명시하면_키가_없어도_목업() {
        runner.withPropertyValues("wearly.ai.provider= MOCK ", "wearly.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiProvider.class)).isInstanceOf(MockAiProvider.class);
                });
    }

    private static void assertStartupFails(AssertableApplicationContext context, String... messageParts) {
        assertThat(context).hasFailed();
        Throwable failure = context.getStartupFailure();
        assertThat(failure).rootCause().isInstanceOf(AiProviderConfigException.class);
        for (String part : messageParts) {
            assertThat(failure).rootCause().hasMessageContaining(part);
        }
        // 시작 실패 화면에 원인과 해결 방법(목업 명령)이 나온다
        FailureAnalysis analysis = new AiProviderFailureAnalyzer().analyze(failure);
        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).startsWith("AI 공급자 설정 오류: ");
        assertThat(analysis.getAction()).contains("./gradlew bootRunMock");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WearlyProperties.class)
    @Import(AiProviderConfig.class)
    static class TestConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
