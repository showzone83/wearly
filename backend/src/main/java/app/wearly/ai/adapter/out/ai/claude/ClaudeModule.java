package app.wearly.ai.adapter.out.ai.claude;

import app.wearly.ai.adapter.out.ai.AiProviderModule;
import app.wearly.ai.adapter.out.ai.ApiKeyChecks;
import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.ProviderOptions;
import app.wearly.ai.adapter.out.ai.Secrets;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.ProviderDefaults;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.models.ModelListParams;
import java.io.InterruptedIOException;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Claude 모듈 (Anthropic Java SDK): 클라이언트 만들기와 키 확인(models().list() = GET /v1/models) */
@Component
public class ClaudeModule implements AiProviderModule {

  private final JsonObjectParser parser;

  public ClaudeModule(JsonMapper jsonMapper) {
    this.parser = new JsonObjectParser(jsonMapper);
  }

  @Override
  public AiProviderType type() {
    return AiProviderType.CLAUDE;
  }

  @Override
  public AiModelPort create(AiProviderSetting s, String key, ProviderOptions o) {
    AnthropicOkHttpClient.Builder b =
        AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(o.timeout())
            // 실패하면 다음 공급자로 넘어가므로 SDK 재시도는 한 번만
            .maxRetries(1);
    if (o.baseUrl() != null) {
      b.baseUrl(o.baseUrl());
    }
    AnthropicClient client = b.build();
    Integer maxTokens = o.integer("maxTokens");
    return new ClaudeAiProvider(
        s.providerId(),
        client,
        s.model(),
        Objects.toString(o.raw().get("effort"), ProviderDefaults.CLAUDE_EFFORT),
        maxTokens == null ? ClaudeAiProvider.DEFAULT_MAX_TOKENS : maxTokens,
        parser,
        key);
  }

  @Override
  public void verify(String apiKey, ProviderOptions o) {
    AnthropicOkHttpClient.Builder b =
        AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(o.timeout()).maxRetries(0);
    if (o.baseUrl() != null) {
      b.baseUrl(o.baseUrl());
    }
    AnthropicClient client = b.build();
    try {
      client.models().list(ModelListParams.builder().limit(1L).build());
    } catch (AnthropicServiceException e) {
      throw new AiProviderException(
          "API_" + e.statusCode(),
          ApiKeyChecks.rejected("Claude", e.statusCode(), e.getMessage(), apiKey));
    } catch (AnthropicException e) {
      String reason = isTimeout(e) ? "TIMEOUT" : "API_CLIENT";
      throw new AiProviderException(
          reason, "Claude 호출 실패: " + Secrets.scrub(e.getMessage(), apiKey));
    } finally {
      client.close();
    }
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getClass().getSimpleName().contains("Timeout") || t instanceof InterruptedIOException) {
        return true;
      }
    }
    return false;
  }
}
