package app.wearly.ai.adapter.out.ai.claude;

import app.wearly.ai.adapter.out.ai.AiProviderModule;
import app.wearly.ai.adapter.out.ai.ApiKeyChecks;
import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.ProviderOptions;
import app.wearly.ai.adapter.out.ai.Secrets;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.ProviderDefaults;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.models.ModelInfo;
import com.anthropic.models.models.ModelListParams;
import java.io.InterruptedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Claude 모듈 (Anthropic Java SDK): 클라이언트 만들기, 키 확인과 모델 목록(둘 다 models().list() = GET /v1/models) */
@Component
public class ClaudeModule implements AiProviderModule {

  /** 모델 목록 한 쪽 크기 (API 최대 1000) */
  static final long PAGE_SIZE = 100;

  /** 모델 목록을 이만큼까지만 읽는다 (쪽 넘김이 끝나지 않아도 멈추게) */
  static final int MAX_MODELS = 500;

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
    AnthropicClient client = listClient(apiKey, o);
    try {
      client.models().list(ModelListParams.builder().limit(1L).build());
    } catch (AnthropicException e) {
      throw failure(e, apiKey);
    } finally {
      client.close();
    }
  }

  /** models().list()를 쪽 넘김으로 끝까지(최대 MAX_MODELS개). 폐기(RETIRED)된 모델은 뺀다 */
  @Override
  public List<AiModelEntry> listModels(String apiKey, ProviderOptions o) {
    AnthropicClient client = listClient(apiKey, o);
    try {
      List<AiModelEntry> out = new ArrayList<>();
      for (ModelInfo m :
          client.models().list(ModelListParams.builder().limit(PAGE_SIZE).build()).autoPager()) {
        if (out.size() >= MAX_MODELS) {
          break;
        }
        String id = m._id().asKnown().orElse(null);
        boolean retired =
            m._lifecycle().asKnown().map(ModelInfo.Lifecycle.RETIRED::equals).orElse(false);
        if (id == null || id.isBlank() || retired) {
          continue;
        }
        out.add(
            AiModelEntry.live(
                id,
                m._displayName().asKnown().orElse(null),
                m._createdAt().asKnown().map(OffsetDateTime::toInstant).orElse(null)));
      }
      return out;
    } catch (AnthropicException e) {
      throw failure(e, apiKey);
    } finally {
      client.close();
    }
  }

  /** 키 확인·모델 목록용 클라이언트 (재시도 없음) */
  private static AnthropicClient listClient(String apiKey, ProviderOptions o) {
    AnthropicOkHttpClient.Builder b =
        AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(o.timeout()).maxRetries(0);
    if (o.baseUrl() != null) {
      b.baseUrl(o.baseUrl());
    }
    return b.build();
  }

  /** SDK 예외 → AiProviderException (API_상태·BAD_RESPONSE·TIMEOUT·API_CLIENT). 메시지에서 키를 지운다 */
  private static AiProviderException failure(AnthropicException e, String apiKey) {
    if (e instanceof AnthropicServiceException se) {
      return new AiProviderException(
          "API_" + se.statusCode(),
          ApiKeyChecks.rejected("Claude", se.statusCode(), se.getMessage(), apiKey));
    }
    if (e instanceof AnthropicInvalidDataException) {
      return new AiProviderException("BAD_RESPONSE", "Claude 모델 목록 응답 형식을 읽지 못했어요.");
    }
    String reason = isTimeout(e) ? "TIMEOUT" : "API_CLIENT";
    return new AiProviderException(
        reason, "Claude 호출 실패: " + Secrets.scrub(e.getMessage(), apiKey));
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
