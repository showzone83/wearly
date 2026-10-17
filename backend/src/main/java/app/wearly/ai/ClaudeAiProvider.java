package app.wearly.ai;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claude 공급자. 사진(base64)과 허용 값 목록을 주고 구조화된 JSON 출력(output_config.format)으로 받는다.
 * 스키마의 enum이 1차로 값을 막고, SuggestionParser가 서버에서 한 번 더 검증한다.
 */
public class ClaudeAiProvider implements AiProvider {

    /** Claude 비전이 받는 형식. HEIC는 받지 않으므로 앱에서 JPEG로 바꿔 보내야 한다. */
    static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** 거절(refusal) 시 서버 측 폴백 (Claude API 베타) */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    static final long MAX_TOKENS = 16000L;

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;
    private final SuggestionParser parser;

    public ClaudeAiProvider(AnthropicClient client, String model, String effort, SuggestionParser parser) {
        this.client = client;
        this.model = model;
        this.effort = OutputConfig.Effort.of(effort.toLowerCase());
        this.parser = parser;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiResult analyze(AiImage image) {
        if (!SUPPORTED_MEDIA_TYPES.contains(image.mediaType())) {
            throw new AiProviderException("UNSUPPORTED_MEDIA", "Claude가 받지 않는 형식이에요: " + image.mediaType());
        }
        Message message;
        try {
            message = client.messages().create(buildParams(image));
        } catch (AnthropicServiceException e) {
            throw new AiProviderException("API_" + e.statusCode(), "Claude API 오류: " + e.getMessage(), e);
        } catch (AnthropicException e) {
            throw new AiProviderException("API_CLIENT", "Claude 호출 실패: " + e.getMessage(), e);
        }
        return toResult(message);
    }

    MessageCreateParams buildParams(AiImage image) {
        ImageBlockParam imageBlock = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(image.mediaType()))
                        .data(Base64.getEncoder().encodeToString(image.bytes()))
                        .build())
                .build();
        TextBlockParam prompt = TextBlockParam.builder().text(prompt()).build();
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .addUserMessageOfBlockParams(List.of(ContentBlockParam.ofImage(imageBlock), ContentBlockParam.ofText(prompt)))
                .outputConfig(OutputConfig.builder()
                        .effort(effort)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build())
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
    }

    AiResult toResult(Message message) {
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
        AiSuggestion suggestion = parser.parse(text);
        return new AiResult(suggestion, message.model().asString(), message.usage().inputTokens(),
                message.usage().outputTokens());
    }

    static String prompt() {
        String pairs = Arrays.stream(Category.values())
                .map(c -> c.name() + ": " + Arrays.stream(SubType.values())
                        .filter(s -> s.belongsTo(c))
                        .map(s -> s.name() + "(" + s.label() + ")")
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
        String colors = Arrays.stream(ColorKey.values())
                .map(c -> c.code() + "(" + c.label() + ")")
                .collect(Collectors.joining(", "));
        return """
                직장인 옷장 앱에 등록할 옷 사진이다. 사진의 주된 아이템 하나를 보고 아래 값을 정해 JSON으로 답한다.

                - name: 한국어 아이템 이름, 20자 이내 (예: "네이비 울 니트")
                - category와 subType: 반드시 아래 짝 중 하나
                %s
                - color: 가장 넓은 면적의 색 하나. %s
                - pattern: SOLID(무지), STRIPE(줄무늬), CHECK(체크), ETC(그 밖의 무늬)
                - seasons: 입기 좋은 계절 1~4개. SPRING, SUMMER, FALL, WINTER
                - styles: 어울리는 스타일 1~2개. NEAT(단정), MINIMAL(미니멀), CASUAL(캐주얼), CLASSIC(클래식), STREET(스트릿)
                - formality: 1(편한 데일리), 2(비즈니스 캐주얼), 3(포멀)
                - confidence: 판단에 대한 확신 0~1

                사진이 흐리거나 옷이 아니면 가장 가까운 값을 고르고 confidence를 낮게 준다.
                """.formatted(pairs, colors);
    }

    /** 허용 값을 enum으로 박은 JSON 스키마 */
    static JsonOutputFormat.Schema schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", Map.of("type", "string"));
        props.put("category", enumOf(Arrays.stream(Category.values()).map(Enum::name).toList()));
        props.put("subType", enumOf(Arrays.stream(SubType.values()).map(Enum::name).toList()));
        props.put("color", enumOf(Arrays.stream(ColorKey.values()).map(ColorKey::code).toList()));
        props.put("pattern", enumOf(Arrays.stream(Pattern.values()).map(Enum::name).toList()));
        props.put("seasons", Map.of("type", "array",
                "items", enumOf(Arrays.stream(Season.values()).map(Enum::name).toList())));
        props.put("styles", Map.of("type", "array",
                "items", enumOf(Arrays.stream(StyleTag.values()).map(Enum::name).toList())));
        props.put("formality", Map.of("type", "integer", "enum", List.of(1, 2, 3)));
        props.put("confidence", Map.of("type", "number"));
        return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(props))
                .putAdditionalProperty("required", JsonValue.from(List.copyOf(props.keySet())))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }
}
