package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.adapter.out.ai.claude.ClaudeAiProvider;
import app.wearly.ai.adapter.out.ai.gemini.GeminiAiProvider;
import app.wearly.ai.adapter.out.ai.mock.MockAiProvider;
import app.wearly.ai.adapter.out.ai.openai.OpenAiProvider;
import app.wearly.ai.application.port.out.AiModelFactoryPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.ProviderDefaults;
import app.wearly.common.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * tb_ai_provider 행 + 키로 공급자 구현을 만든다. 같은 설정이면 만든 클라이언트를 다시 쓴다 (행이 바뀌면 updated_at이, 키가 바뀌면 키 해시가 달라져
 * 새로 만든다).
 */
@Component
public class AiProviderFactory implements AiModelFactoryPort {

  private final WearlyProperties.Ai props;
  private final JsonMapper jsonMapper;
  private final JsonObjectParser parser;
  private final MockAiProvider mock = new MockAiProvider();
  private final Map<String, AiModelPort> cache = new ConcurrentHashMap<>();

  public AiProviderFactory(WearlyProperties properties, JsonMapper jsonMapper) {
    this.props = properties.ai();
    this.jsonMapper = jsonMapper;
    this.parser = new JsonObjectParser(jsonMapper);
  }

  @Override
  public AiModelPort mock() {
    return mock;
  }

  @Override
  public AiModelPort create(AiProviderSetting s, String key) {
    // 키 자체는 캐시 키에 넣지 않고 해시만 쓴다 (메모리 안에서만 쓰는 값)
    String cacheKey =
        String.join(
            "|",
            s.providerId(),
            s.type().name(),
            s.model(),
            s.apiKeyEnv(),
            String.valueOf(s.updatedAt()),
            Integer.toHexString(key.hashCode()),
            String.valueOf(s.options()));
    AiModelPort cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    synchronized (cache) {
      // 같은 공급자의 예전 설정으로 만든 것은 버린다
      cache.keySet().removeIf(old -> old.startsWith(s.providerId() + "|") && !old.equals(cacheKey));
      return cache.computeIfAbsent(cacheKey, k -> build(s, key));
    }
  }

  private AiModelPort build(AiProviderSetting s, String key) {
    Map<String, Object> o = s.options();
    Duration timeout =
        o.get("timeoutSeconds") instanceof Number n
            ? Duration.ofSeconds(n.longValue())
            : props.timeout();
    String baseUrl = props.allowBaseUrl() && o.get("baseUrl") instanceof String u ? u : null;
    Integer maxTokens = o.get("maxTokens") instanceof Number n ? n.intValue() : null;
    Double temperature = o.get("temperature") instanceof Number n ? n.doubleValue() : null;
    return switch (s.type()) {
      case CLAUDE -> {
        AnthropicOkHttpClient.Builder b =
            AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(timeout)
                // 실패하면 다음 공급자로 넘어가므로 SDK 재시도는 한 번만
                .maxRetries(1);
        if (baseUrl != null) {
          b.baseUrl(baseUrl);
        }
        AnthropicClient client = b.build();
        String effort = Objects.toString(o.get("effort"), ProviderDefaults.CLAUDE_EFFORT);
        yield new ClaudeAiProvider(
            s.providerId(),
            client,
            s.model(),
            effort,
            maxTokens == null ? ClaudeAiProvider.DEFAULT_MAX_TOKENS : maxTokens,
            parser,
            key);
      }
      case OPENAI ->
          new OpenAiProvider(
              s.providerId(),
              s.model(),
              key,
              baseUrl,
              timeout,
              maxTokens,
              temperature,
              o.get("detail") instanceof String d ? d : null,
              jsonMapper,
              parser);
      case GEMINI ->
          new GeminiAiProvider(
              s.providerId(),
              s.model(),
              key,
              baseUrl,
              timeout,
              maxTokens,
              temperature,
              o.get("schemaField") instanceof String f ? f : null,
              jsonMapper,
              parser);
    };
  }
}
