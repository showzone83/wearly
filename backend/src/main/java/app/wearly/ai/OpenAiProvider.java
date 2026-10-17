package app.wearly.ai;

import app.wearly.code.CodeSnapshot;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI 공급자: Responses API(POST {baseUrl}/responses)를 RestClient로 부른다.
 * - 입력: user 메시지에 input_text(프롬프트) + input_image(data URL base64)
 * - 출력: text.format = { type: json_schema, name, schema, strict: true } 구조화 출력
 * - 사용량: usage.input_tokens / usage.output_tokens (output에는 추론 토큰 포함)
 * 형식 출처: OpenAI 공식 OpenAPI 명세(openai/openai-openapi)의 CreateResponse·Response 스키마.
 */
public class OpenAiProvider implements AiProvider {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /** OpenAI 이미지 입력이 받는 형식 (HEIC 불가 → 다음 공급자로) */
    static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    static final String SCHEMA_NAME = "wearly_item_suggestion";

    private final String id;
    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final Double temperature;
    private final int maxTokens;
    private final JsonHttp http;
    private final SuggestionParser parser;

    OpenAiProvider(String id, String model, String baseUrl, String apiKey, Double temperature, int maxTokens,
            JsonHttp http, SuggestionParser parser) {
        this.id = id;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.http = http;
        this.parser = parser;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiResult analyze(AiImage image, CodeSnapshot codes) {
        if (!SUPPORTED_MEDIA_TYPES.contains(image.mediaType())) {
            throw new AiProviderException("UNSUPPORTED_MEDIA", "OpenAI가 받지 않는 형식이에요: " + image.mediaType());
        }
        JsonNode res = http.post(baseUrl + "/responses", Map.of("Authorization", "Bearer " + apiKey),
                buildRequest(image, codes));
        return toResult(res, codes);
    }

    Map<String, Object> buildRequest(AiImage image, CodeSnapshot codes) {
        String dataUrl = "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
        Map<String, Object> text = Map.of("type", "input_text", "text", AiPrompt.prompt(codes));
        Map<String, Object> img = Map.of("type", "input_image", "image_url", dataUrl, "detail", "auto");
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", SCHEMA_NAME);
        format.put("schema", AiPrompt.schema(codes, true));
        format.put("strict", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(Map.of("role", "user", "content", List.of(text, img))));
        body.put("text", Map.of("format", format));
        body.put("max_output_tokens", maxTokens);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        body.put("store", false); // 응답을 OpenAI에 보관하지 않는다
        return body;
    }

    AiResult toResult(JsonNode res, CodeSnapshot codes) {
        String status = res.path("status").asString("");
        if ("incomplete".equals(status)) {
            String reason = res.path("incomplete_details").path("reason").asString("");
            if ("max_output_tokens".equals(reason)) {
                throw new AiProviderException("MAX_TOKENS", "응답이 잘렸어요.");
            }
            if ("content_filter".equals(reason)) {
                throw new AiProviderException("REFUSAL", "OpenAI 콘텐츠 필터가 응답을 막았어요.");
            }
            throw new AiProviderException("INCOMPLETE", "응답이 끝나지 않았어요: " + reason);
        }
        if (!status.isEmpty() && !"completed".equals(status)) {
            String code = res.path("error").path("code").asString(status);
            throw new AiProviderException("STATUS_" + status.toUpperCase(), "OpenAI 응답 상태: " + status + " (" + code + ")");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : res.path("output")) {
            if (!"message".equals(item.path("type").asString())) {
                continue; // reasoning 등은 건너뛴다
            }
            for (JsonNode part : item.path("content")) {
                String type = part.path("type").asString();
                if ("refusal".equals(type)) {
                    throw new AiProviderException("REFUSAL", "OpenAI가 분석을 거절했어요.");
                }
                if ("output_text".equals(type)) {
                    text.append(part.path("text").asString(""));
                }
            }
        }
        if (text.isEmpty()) {
            throw new AiProviderException("EMPTY_OUTPUT", "응답에 텍스트가 없어요.");
        }
        AiSuggestion suggestion = parser.parse(text.toString(), codes);
        JsonNode usage = res.path("usage");
        return new AiResult(suggestion, res.path("model").asString(model), longOrNull(usage, "input_tokens"),
                longOrNull(usage, "output_tokens"));
    }

    static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asLong() : null;
    }
}
