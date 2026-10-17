package app.wearly.ai;

import app.wearly.code.CodeSnapshot;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claude 공급자 (Anthropic Java SDK). 사진(base64)과 허용 값 목록을 주고 구조화된 JSON 출력(output_config.format)으로 받는다.
 * 스키마의 enum이 1차로 값을 막고, SuggestionParser가 서버에서 활성 코드로 한 번 더 검증한다.
 */
public class ClaudeAiProvider implements AiProvider {

    /** Claude 비전이 받는 형식. HEIC는 받지 않는다 (다음 공급자로 넘어간다) */
    static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** 거절(refusal) 시 서버 측 폴백 (Claude API 베타) */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    static final long DEFAULT_MAX_TOKENS = 16000L;

    private final String id;
    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;
    private final long maxTokens;
    private final SuggestionParser parser;
    private final String apiKey;

    public ClaudeAiProvider(String id, AnthropicClient client, String model, String effort, long maxTokens,
            SuggestionParser parser, String apiKey) {
        this.id = id;
        this.client = client;
        this.model = model;
        this.effort = OutputConfig.Effort.of(effort.toLowerCase(Locale.ROOT));
        this.maxTokens = maxTokens;
        this.parser = parser;
        this.apiKey = apiKey;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String model() {
        return model;
    }

    AnthropicClient client() {
        return client;
    }

    @Override
    public AiResult analyze(AiImage image, CodeSnapshot codes) {
        if (!SUPPORTED_MEDIA_TYPES.contains(image.mediaType())) {
            throw new AiProviderException("UNSUPPORTED_MEDIA", "Claude가 받지 않는 형식이에요: " + image.mediaType());
        }
        Message message;
        try {
            message = client.messages().create(buildParams(image, codes));
        } catch (AnthropicServiceException e) {
            throw new AiProviderException("API_" + e.statusCode(),
                    "Claude API 오류: " + Redactor.redact(e.getMessage(), apiKey), e);
        } catch (AnthropicIoException e) {
            throw new AiProviderException("TIMEOUT_OR_IO", "Claude 호출 실패(네트워크·시간 초과)", e);
        } catch (AnthropicException e) {
            throw new AiProviderException("API_CLIENT", "Claude 호출 실패: " + Redactor.redact(e.getMessage(), apiKey), e);
        }
        return toResult(message, codes);
    }

    MessageCreateParams buildParams(AiImage image, CodeSnapshot codes) {
        ImageBlockParam imageBlock = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(image.mediaType()))
                        .data(Base64.getEncoder().encodeToString(image.bytes()))
                        .build())
                .build();
        TextBlockParam prompt = TextBlockParam.builder().text(AiPrompt.prompt(codes)).build();
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessageOfBlockParams(List.of(ContentBlockParam.ofImage(imageBlock), ContentBlockParam.ofText(prompt)))
                .outputConfig(OutputConfig.builder()
                        .effort(effort)
                        .format(JsonOutputFormat.builder().schema(schema(codes)).build())
                        .build())
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
    }

    AiResult toResult(Message message, CodeSnapshot codes) {
        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new AiProviderException("REFUSAL", "Claude가 분석을 거절했어요.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiProviderException("MAX_TOKENS", "응답이 잘렸어요.");
        }
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new AiProviderException("EMPTY_OUTPUT", "응답에 텍스트가 없어요.");
        }
        AiSuggestion suggestion = parser.parse(text, codes);
        return new AiResult(suggestion, message.model().asString(), message.usage().inputTokens(),
                message.usage().outputTokens());
    }

    /** 허용 값을 enum으로 박은 JSON 스키마 (AiPrompt 공통 스키마) */
    static JsonOutputFormat.Schema schema(CodeSnapshot codes) {
        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> e : AiPrompt.schema(codes, true).entrySet()) {
            b.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        return b.build();
    }
}
