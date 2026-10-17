package app.wearly.ai;

import app.wearly.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * WEARLY_AI_PROVIDER로 공급자를 고른다.
 * - mock: 항상 목업
 * - claude: Claude (ANTHROPIC_API_KEY 필수, 없으면 기동 실패)
 * - auto(기본): 키가 있으면 Claude, 없으면 목업
 */
@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    AiProvider aiProvider(WearlyProperties properties, JsonMapper jsonMapper) {
        WearlyProperties.Ai ai = properties.ai();
        String mode = ai.provider().strip().toLowerCase(Locale.ROOT);
        boolean useClaude = switch (mode) {
            case "mock" -> false;
            case "claude" -> {
                if (!ai.hasApiKey()) {
                    throw new IllegalStateException("WEARLY_AI_PROVIDER=claude인데 ANTHROPIC_API_KEY가 없어요.");
                }
                yield true;
            }
            case "auto" -> ai.hasApiKey();
            default -> throw new IllegalStateException("WEARLY_AI_PROVIDER는 mock, claude, auto 중 하나여야 해요: " + ai.provider());
        };
        if (!useClaude) {
            log.info("AI 공급자: mock");
            return new MockAiProvider();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(ai.apiKey())
                .timeout(ai.timeout())
                .maxRetries(2)
                .build();
        log.info("AI 공급자: claude (model={}, effort={})", ai.model(), ai.effort());
        return new ClaudeAiProvider(client, ai.model(), ai.effort(), new SuggestionParser(jsonMapper));
    }
}
