package app.wearly.ai;

import app.wearly.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급자 고르기 (doc/07_관리자_사이트.md 3장).
 * - 목업 실행: 언제나 목업 하나.
 * - 그 밖: tb_ai_provider에서 사용(enabled)·모델·키를 모두 갖춘 행을 우선순위 순으로.
 * 공급자 객체(SDK·HTTP 클라이언트)는 행이 바뀔 때까지 재사용한다.
 */
@Component
public class AiProviderRegistry {

    private final AiMode mode;
    private final AiProviderSettingRepository settings;
    private final ApiKeyResolver keys;
    private final JsonMapper jsonMapper;
    private final Duration defaultTimeout;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public AiProviderRegistry(WearlyProperties properties, AiProviderSettingRepository settings, ApiKeyResolver keys,
            JsonMapper jsonMapper) {
        this.mode = AiMode.parse(properties.ai().provider());
        this.settings = settings;
        this.keys = keys;
        this.jsonMapper = jsonMapper;
        this.defaultTimeout = properties.ai().timeout();
    }

    public AiMode mode() {
        return mode;
    }

    /** 분석에 시도할 공급자 (순서대로). 비어 있으면 쓸 수 있는 공급자가 없다 */
    @Transactional(readOnly = true)
    public List<AiProvider> candidates() {
        if (mode == AiMode.MOCK) {
            return List.of(new MockAiProvider());
        }
        return settings.findAllByOrderByPriorityAscProviderIdAsc().stream()
                .filter(this::usable)
                .map(this::provider)
                .toList();
    }

    /** 쓸 수 있는 공급자 설정 (시작 로그용) */
    @Transactional(readOnly = true)
    public List<AiProviderSetting> usableSettings() {
        return settings.findAllByOrderByPriorityAscProviderIdAsc().stream().filter(this::usable).toList();
    }

    public boolean keyConfigured(AiProviderSetting s) {
        return s.typeEnum() == AiProviderType.MOCK || keys.configured(s.getApiKeyEnv());
    }

    /** 사용·모델·키를 모두 갖췄는지 */
    public boolean usable(AiProviderSetting s) {
        return s.isEnabled() && s.hasModel() && keyConfigured(s);
    }

    /** 설정 한 행으로 공급자를 만든다 (모델·키가 있어야 한다. 사용 여부는 보지 않는다: 관리자 시험 분석) */
    public AiProvider provider(AiProviderSetting s) {
        String key = s.typeEnum() == AiProviderType.MOCK ? "" : keys.resolve(s.getApiKeyEnv())
                .orElseThrow(() -> new IllegalStateException("키가 없는 공급자: " + s.getProviderId()));
        String fingerprint = s.getType() + "|" + s.getModel() + "|" + s.getApiKeyEnv() + "|" + s.getOptions() + "|"
                + s.getUpdatedAt() + "|" + Objects.hashCode(key);
        Cached cached = cache.get(s.getProviderId());
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.provider();
        }
        AiProvider created = create(s, key);
        Cached old = cache.put(s.getProviderId(), new Cached(fingerprint, created));
        if (old != null) {
            close(old.provider());
        }
        return created;
    }

    /** 관리자 API로 행을 바꾸거나 지우면 부른다 */
    public void evict(String providerId) {
        Optional.ofNullable(cache.remove(providerId)).ifPresent(c -> close(c.provider()));
    }

    private AiProvider create(AiProviderSetting s, String key) {
        Map<String, Object> o = s.getOptions() == null ? Map.of() : s.getOptions();
        Duration timeout = o.get("timeoutSeconds") instanceof Number n ? Duration.ofSeconds(n.longValue()) : defaultTimeout;
        int maxTokens = o.get("maxTokens") instanceof Number n ? n.intValue() : (int) ClaudeAiProvider.DEFAULT_MAX_TOKENS;
        Double temperature = o.get("temperature") instanceof Number n ? n.doubleValue() : null;
        String baseUrl = o.get("baseUrl") instanceof String b && !b.isBlank() ? b : null;
        SuggestionParser parser = new SuggestionParser(jsonMapper);
        return switch (s.typeEnum()) {
            case CLAUDE -> {
                AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                        .apiKey(key)
                        .timeout(timeout)
                        .maxRetries(2);
                if (baseUrl != null) {
                    b.baseUrl(baseUrl);
                }
                String effort = o.get("effort") instanceof String e ? e : "medium";
                yield new ClaudeAiProvider(s.getProviderId(), b.build(), s.getModel(), effort, maxTokens, parser, key);
            }
            case OPENAI -> new OpenAiProvider(s.getProviderId(), s.getModel(),
                    baseUrl == null ? OpenAiProvider.DEFAULT_BASE_URL : baseUrl, key, temperature, maxTokens,
                    new JsonHttp(jsonMapper, timeout, "OpenAI", key), parser);
            case GEMINI -> new GeminiAiProvider(s.getProviderId(), s.getModel(),
                    baseUrl == null ? GeminiAiProvider.DEFAULT_BASE_URL : baseUrl, key, temperature, maxTokens,
                    new JsonHttp(jsonMapper, timeout, "Gemini", key), parser);
            case MOCK -> new MockAiProvider(s.getProviderId());
        };
    }

    private static void close(AiProvider provider) {
        if (provider instanceof ClaudeAiProvider claude) {
            AnthropicClient client = claude.client();
            client.close();
        }
    }

    private record Cached(String fingerprint, AiProvider provider) {
    }
}
