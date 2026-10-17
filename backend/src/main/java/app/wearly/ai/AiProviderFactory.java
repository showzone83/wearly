package app.wearly.ai;

import app.wearly.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * tb_ai_provider 행 + 환경변수 키로 공급자 구현을 만든다. 같은 설정이면 만든 클라이언트를 다시 쓴다
 * (행이 바뀌면 updated_at이 달라져 새로 만든다).
 */
@Component
public class AiProviderFactory {

    private final ApiKeyResolver keys;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final SuggestionParser parser;
    private final Map<String, AiProvider> cache = new ConcurrentHashMap<>();

    public AiProviderFactory(ApiKeyResolver keys, WearlyProperties properties, JsonMapper jsonMapper) {
        this.keys = keys;
        this.props = properties.ai();
        this.jsonMapper = jsonMapper;
        this.parser = new SuggestionParser(jsonMapper);
    }

    /**
     * @throws AiProviderException 모델이 없거나(MODEL_MISSING) 키가 없을 때(KEY_MISSING)
     */
    public AiProvider create(AiProviderSetting s) {
        if (!s.hasModel()) {
            throw new AiProviderException("MODEL_MISSING", "모델이 설정되지 않았어요.");
        }
        String key = keys.resolve(s.getApiKeyEnv())
                .orElseThrow(() -> new AiProviderException("KEY_MISSING", s.getApiKeyEnv() + " 환경변수가 비어 있어요."));
        // 키 자체는 캐시 키에 넣지 않고 해시만 쓴다 (메모리 안에서만 쓰는 값)
        String cacheKey = String.join("|", s.getProviderId(), s.getType().name(), s.getModel(), s.getApiKeyEnv(),
                String.valueOf(s.getUpdatedAt()), Integer.toHexString(key.hashCode()), String.valueOf(s.getOptions()));
        AiProvider cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        synchronized (cache) {
            // 같은 공급자의 예전 설정으로 만든 것은 버린다
            cache.keySet().removeIf(old -> old.startsWith(s.getProviderId() + "|") && !old.equals(cacheKey));
            return cache.computeIfAbsent(cacheKey, k -> build(s, key));
        }
    }

    private AiProvider build(AiProviderSetting s, String key) {
        Map<String, Object> o = s.getOptions();
        Duration timeout = o.get("timeoutSeconds") instanceof Number n ? Duration.ofSeconds(n.longValue()) : props.timeout();
        String baseUrl = props.allowBaseUrl() && o.get("baseUrl") instanceof String u ? u : null;
        Integer maxTokens = o.get("maxTokens") instanceof Number n ? n.intValue() : null;
        Double temperature = o.get("temperature") instanceof Number n ? n.doubleValue() : null;
        return switch (s.getType()) {
            case CLAUDE -> {
                AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                        .apiKey(key)
                        .timeout(timeout)
                        // 실패하면 다음 공급자로 넘어가므로 SDK 재시도는 한 번만
                        .maxRetries(1);
                if (baseUrl != null) {
                    b.baseUrl(baseUrl);
                }
                AnthropicClient client = b.build();
                String effort = Objects.toString(o.get("effort"), "medium");
                yield new ClaudeAiProvider(s.getProviderId(), client, s.getModel(), effort,
                        maxTokens == null ? ClaudeAiProvider.DEFAULT_MAX_TOKENS : maxTokens, parser, key);
            }
            case OPENAI -> new OpenAiProvider(s.getProviderId(), s.getModel(), key, baseUrl, timeout, maxTokens,
                    temperature, o.get("detail") instanceof String d ? d : null, jsonMapper, parser);
            case GEMINI -> new GeminiAiProvider(s.getProviderId(), s.getModel(), key, baseUrl, timeout, maxTokens,
                    temperature, o.get("schemaField") instanceof String f ? f : null, jsonMapper, parser);
        };
    }
}
