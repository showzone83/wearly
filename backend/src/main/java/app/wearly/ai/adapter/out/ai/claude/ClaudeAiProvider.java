package app.wearly.ai.adapter.out.ai.claude;

import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.Secrets;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.ProviderDefaults;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claude 공급자 (Anthropic Java SDK). 사진(base64, 선택)과 프롬프트를 주고 구조화된 JSON 출력(output_config.format)으로
 * 받는다. 무엇을 묻고 어떻게 검증할지는 호출한 쪽이 정한다 (사진 분석은 도메인 AnalysisPrompt·AiSuggestionValidator).
 *
 * <p>프롬프트 캐싱: 지시문(instructions)은 요청마다 같은 고정 부분이라 system 블록 하나로 보내고, promptCache가 켜져 있으면 그 블록 끝에
 * cache_control(ephemeral, 기본 5분)을 붙인다. 캐시 앞부분은 tools → system 순서라 이 블록까지가 캐시되고, 요청마다 바뀌는 사진·사용자
 * 메시지는 그 뒤에 온다. output_config.format(스키마)을 바꾸면 캐시가 깨지므로 스키마도 바이트 단위로 같아야 한다.
 */
public class ClaudeAiProvider implements AiModelPort {

  /** Claude 비전이 받는 형식. HEIC는 받지 않으므로 앱에서 JPEG로 바꿔 보내야 한다. */
  static final Set<String> SUPPORTED_MEDIA_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  /** 거절(refusal) 시 서버 측 폴백 (Claude API 베타) */
  public static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  public static final long DEFAULT_MAX_TOKENS = ProviderDefaults.CLAUDE_MAX_TOKENS;

  private final String id;
  private final AnthropicClient client;
  private final String model;
  private final OutputConfig.Effort effort;
  private final long maxTokens;
  private final JsonObjectParser parser;
  private final String apiKey;
  private final boolean promptCache;

  /** 프롬프트 캐싱 켜짐 */
  public ClaudeAiProvider(
      String id,
      AnthropicClient client,
      String model,
      String effort,
      long maxTokens,
      JsonObjectParser parser,
      String apiKey) {
    this(id, client, model, effort, maxTokens, parser, apiKey, true);
  }

  /**
   * @param promptCache true면 지시문(system) 끝에 cache_control을 붙인다 (wearly.ai.prompt-cache.enabled)
   */
  public ClaudeAiProvider(
      String id,
      AnthropicClient client,
      String model,
      String effort,
      long maxTokens,
      JsonObjectParser parser,
      String apiKey,
      boolean promptCache) {
    this.id = id;
    this.client = client;
    this.model = model;
    this.effort = OutputConfig.Effort.of(effort.toLowerCase());
    this.maxTokens = maxTokens;
    this.parser = parser;
    this.apiKey = apiKey;
    this.promptCache = promptCache;
  }

  @Override
  public String name() {
    return id;
  }

  @Override
  public String model() {
    return model;
  }

  /**
   * 사진이 있으면 user 메시지 = [image(base64), text], 없으면 text만. 지시문이 있으면 system (promptCache면 끝에
   * cache_control). 구조화 출력은 output_config.format = 호출한 쪽의 스키마
   */
  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image) {
    if (image != null && !SUPPORTED_MEDIA_TYPES.contains(image.mediaType())) {
      throw new AiProviderException(
          "UNSUPPORTED_MEDIA", "Claude가 받지 않는 형식이에요: " + image.mediaType());
    }
    Message message = send(buildParams(prompt, image));
    Usage usage = message.usage();
    long cacheRead = usage.cacheReadInputTokens().orElse(0L);
    long cacheWrite = usage.cacheCreationInputTokens().orElse(0L);
    return new AiJsonResult(
        parser.parseObject(outputText(message)),
        message.model().asString(),
        // input_tokens는 마지막 캐시 지점 뒤(캐시 안 된 부분)만 센다. 기록은 다른 공급자와 같게 입력 전체로 남긴다
        usage.inputTokens() + cacheRead + cacheWrite,
        usage.outputTokens(),
        usage.cacheReadInputTokens().orElse(null),
        usage.cacheCreationInputTokens().orElse(null));
  }

  private Message send(MessageCreateParams params) {
    try {
      return client.messages().create(params);
    } catch (AnthropicServiceException e) {
      throw new AiProviderException(
          "API_" + e.statusCode(),
          Secrets.upstream("Claude", e.statusCode(), e.getMessage(), apiKey));
    } catch (AnthropicException e) {
      String reason = isTimeout(e) ? "TIMEOUT" : "API_CLIENT";
      throw new AiProviderException(
          reason, "Claude 호출 실패: " + Secrets.scrub(e.getMessage(), apiKey));
    }
  }

  MessageCreateParams buildParams(AiJsonPrompt prompt, AiImage image) {
    MessageCreateParams.Builder b = MessageCreateParams.builder().model(model).maxTokens(maxTokens);
    if (prompt.instructions() != null) {
      if (promptCache) {
        // 고정 지시문 끝에 캐시 지점 (TTL 기본 5분). 지시문이 모델별 최소 길이보다 짧으면 캐시되지 않을 뿐 오류는 없다
        b.systemOfTextBlockParams(
            List.of(
                TextBlockParam.builder()
                    .text(prompt.instructions())
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()));
      } else {
        b.system(prompt.instructions());
      }
    }
    if (image == null) {
      b.addUserMessage(prompt.prompt());
    } else {
      ImageBlockParam imageBlock =
          ImageBlockParam.builder()
              .source(
                  Base64ImageSource.builder()
                      .mediaType(Base64ImageSource.MediaType.of(image.mediaType()))
                      .data(Base64.getEncoder().encodeToString(image.bytes()))
                      .build())
              .build();
      TextBlockParam text = TextBlockParam.builder().text(prompt.prompt()).build();
      b.addUserMessageOfBlockParams(
          List.of(ContentBlockParam.ofImage(imageBlock), ContentBlockParam.ofText(text)));
    }
    return b.outputConfig(
            OutputConfig.builder()
                .effort(effort)
                .format(JsonOutputFormat.builder().schema(schema(prompt.schema())).build())
                .build())
        .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        .build();
  }

  /** 거절·잘림을 걸러 내고 text 블록을 이어 붙인다 */
  private static String outputText(Message message) {
    StopReason stop = message.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stop)) {
      throw new AiProviderException("REFUSAL", "Claude가 분석을 거절했어요.");
    }
    if (StopReason.MAX_TOKENS.equals(stop)) {
      throw new AiProviderException("MAX_TOKENS", "응답이 잘렸어요.");
    }
    String text =
        message.content().stream()
            .flatMap(block -> block.text().stream())
            .map(t -> t.text())
            .collect(Collectors.joining());
    if (text.isBlank()) {
      throw new AiProviderException("EMPTY_OUTPUT", "응답에 텍스트가 없어요.");
    }
    return text;
  }

  static JsonOutputFormat.Schema schema(Map<String, Object> jsonSchema) {
    JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
    for (Map.Entry<String, Object> e : jsonSchema.entrySet()) {
      builder.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
    }
    return builder.build();
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      String name = t.getClass().getSimpleName();
      if (name.contains("Timeout") || t instanceof java.io.InterruptedIOException) {
        return true;
      }
    }
    return false;
  }
}
