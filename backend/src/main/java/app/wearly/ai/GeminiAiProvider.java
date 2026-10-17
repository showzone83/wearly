package app.wearly.ai;

import app.wearly.code.CodeSnapshot;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Gemini 공급자: Gemini API generateContent(POST {baseUrl}/models/{model}:generateContent)를 RestClient로 부른다.
 * - 인증: x-goog-api-key 헤더 (키를 URL 쿼리에 넣지 않는다)
 * - 입력: contents[0].parts = inlineData(mimeType, base64) + text(프롬프트)
 * - 출력: generationConfig.responseMimeType=application/json + responseJsonSchema(JSON Schema) 구조화 출력
 * - 사용량: usageMetadata.promptTokenCount / candidatesTokenCount (+ thoughtsTokenCount를 출력에 더한다)
 * 형식 출처: Gemini API 공식 discovery 문서(generativelanguage v1beta)와 Google 공식 google-genai SDK.
 */
public class GeminiAiProvider implements AiProvider {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    /** Gemini 이미지 입력이 받는 형식 (HEIC도 받는다) */
    static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic",
            "image/heif");

    private final String id;
    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final Double temperature;
    private final int maxTokens;
    private final JsonHttp http;
    private final SuggestionParser parser;

    GeminiAiProvider(String id, String model, String baseUrl, String apiKey, Double temperature, int maxTokens,
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
            throw new AiProviderException("UNSUPPORTED_MEDIA", "Gemini가 받지 않는 형식이에요: " + image.mediaType());
        }
        JsonNode res = http.post(url(), Map.of("x-goog-api-key", apiKey), buildRequest(image, codes));
        return toResult(res, codes);
    }

    /** 모델은 "gemini-…" 또는 "models/gemini-…" 둘 다 받는다 */
    String url() {
        String name = model.startsWith("models/") ? model.substring("models/".length()) : model;
        return baseUrl + "/models/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20")
                + ":generateContent";
    }

    Map<String, Object> buildRequest(AiImage image, CodeSnapshot codes) {
        Map<String, Object> inline = Map.of("inlineData",
                Map.of("mimeType", image.mediaType(), "data", Base64.getEncoder().encodeToString(image.bytes())));
        Map<String, Object> text = Map.of("text", AiPrompt.prompt(codes));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("responseMimeType", "application/json");
        config.put("responseJsonSchema", AiPrompt.schema(codes, false));
        config.put("maxOutputTokens", maxTokens);
        if (temperature != null) {
            config.put("temperature", temperature);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(inline, text))));
        body.put("generationConfig", config);
        return body;
    }

    AiResult toResult(JsonNode res, CodeSnapshot codes) {
        String blockReason = res.path("promptFeedback").path("blockReason").asString(null);
        if (blockReason != null) {
            throw new AiProviderException("REFUSAL", "Gemini가 요청을 막았어요: " + blockReason);
        }
        JsonNode candidate = res.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw new AiProviderException("EMPTY_OUTPUT", "응답 후보가 없어요.");
        }
        String finish = candidate.path("finishReason").asString("");
        if ("MAX_TOKENS".equals(finish)) {
            throw new AiProviderException("MAX_TOKENS", "응답이 잘렸어요.");
        }
        if (!finish.isEmpty() && !"STOP".equals(finish)) {
            throw new AiProviderException("REFUSAL", "Gemini 응답이 중단됐어요: " + finish);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue; // 생각 요약은 건너뛴다
            }
            text.append(part.path("text").asString(""));
        }
        if (text.isEmpty()) {
            throw new AiProviderException("EMPTY_OUTPUT", "응답에 텍스트가 없어요.");
        }
        AiSuggestion suggestion = parser.parse(text.toString(), codes);
        JsonNode usage = res.path("usageMetadata");
        Long input = OpenAiProvider.longOrNull(usage, "promptTokenCount");
        Long candidates = OpenAiProvider.longOrNull(usage, "candidatesTokenCount");
        Long thoughts = OpenAiProvider.longOrNull(usage, "thoughtsTokenCount");
        Long output = candidates == null && thoughts == null ? null
                : (candidates == null ? 0 : candidates) + (thoughts == null ? 0 : thoughts);
        return new AiResult(suggestion, res.path("modelVersion").asString(model), input, output);
    }
}
