package app.wearly.ai;

import app.wearly.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.json.JsonMapper;

/**
 * WEARLY_AI_PROVIDER로 공급자를 고른다.
 * - claude(기본): Claude. ANTHROPIC_API_KEY가 없으면 서버 시작을 실패시킨다 (조용히 목업으로 바뀌지 않는다).
 * - mock: 목업. 명시했을 때만 쓴다 (./gradlew bootRunMock).
 * 그 밖의 값(예전 auto 포함)은 시작 실패.
 */
@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    static final String MISSING_KEY_MESSAGE = "ANTHROPIC_API_KEY가 없습니다. "
            + "backend/.env에 ANTHROPIC_API_KEY=... 를 넣거나 환경변수로 주세요. "
            + "목업으로 띄우려면 ./gradlew bootRunMock (또는 WEARLY_AI_PROVIDER=mock)";

    /** 지연 초기화(spring.main.lazy-initialization)가 켜져도 설정 오류는 시작 시점에 드러나게 즉시 만든다. */
    @Bean
    @Lazy(false)
    AiProvider aiProvider(WearlyProperties properties, JsonMapper jsonMapper) {
        WearlyProperties.Ai ai = properties.ai();
        String mode = ai.provider().strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "mock" -> new MockAiProvider();
            case "claude" -> {
                if (!ai.hasApiKey()) {
                    throw new AiProviderConfigException(MISSING_KEY_MESSAGE);
                }
                AnthropicClient client = AnthropicOkHttpClient.builder()
                        .apiKey(ai.apiKey())
                        .timeout(ai.timeout())
                        .maxRetries(2)
                        .build();
                yield new ClaudeAiProvider(client, ai.model(), ai.effort(), new SuggestionParser(jsonMapper));
            }
            case "auto" -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER=auto는 더 이상 지원하지 않습니다. 기본값 claude를 쓰거나(키 필요), "
                            + "목업은 WEARLY_AI_PROVIDER=mock 또는 ./gradlew bootRunMock으로 띄우세요.");
            default -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER는 claude(기본) 또는 mock이어야 합니다: " + ai.provider());
        };
    }

    /** 서버가 요청을 받기 시작할 때 선택된 공급자를 한 줄 남긴다. */
    @Bean
    ApplicationListener<ApplicationReadyEvent> aiProviderStartupLog(AiProvider aiProvider, WearlyProperties properties) {
        return event -> {
            if ("claude".equals(aiProvider.name())) {
                log.info("AI 공급자: claude (model={}, effort={})", aiProvider.model(), properties.ai().effort());
            } else {
                log.info("AI 공급자: {} (목업 — 실제 Claude를 호출하지 않음)", aiProvider.name());
            }
        };
    }
}
