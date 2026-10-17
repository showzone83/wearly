package app.wearly.ai.adapter.out.ai.gemini;

import app.wearly.ai.adapter.out.ai.HttpCall;
import app.wearly.ai.adapter.out.ai.Secrets;
import app.wearly.ai.adapter.out.ai.SuggestionParser;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AiSuggestion;
import app.wearly.ai.domain.AnalysisPrompt;
import app.wearly.ai.domain.ProviderDefaults;
import app.wearly.common.domain.code.CodeSnapshot;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini 공급자: generateContent(POST {baseUrl}/v1beta/models/{model}:generateContent)를 RestClient로 직접
 * 부른다. 요청 형식은 Gemini API 공식 discovery 문서(generativelanguage v1beta)를 따른다. - 입력: contents[0].parts =
 * [inlineData{mimeType, data(base64)}, text(프롬프트)] - 구조화 출력: generationConfig.responseFormat.text =
 * { mimeType: APPLICATION_JSON, schema } (기본). options.schemaField=responseJsonSchema면
 * generationConfig.responseMimeType + responseJsonSchema로 보낸다. - 결과:
 * candidates[0].content.parts[].text (thought=true 제외), finishReason·promptFeedback.blockReason으로
 * 거절 판단 - 토큰: usageMetadata.promptTokenCount / candidatesTokenCount + thoughtsTokenCount - 키:
 * x-goog-api-key 헤더 (URL 쿼리 key=는 로그·오류 메시지에 남을 수 있어 쓰지 않는다)
 */
public class GeminiAiProvider implements AiModelPort {

  public static final String DEFAULT_BASE_URL = ProviderDefaults.GEMINI_BASE_URL;
  public static final String SCHEMA_RESPONSE_FORMAT =
      ProviderDefaults.GEMINI_SCHEMA_RESPONSE_FORMAT;
  public static final String SCHEMA_RESPONSE_JSON_SCHEMA =
      ProviderDefaults.GEMINI_SCHEMA_RESPONSE_JSON_SCHEMA;
  static final int DEFAULT_MAX_OUTPUT_TOKENS = ProviderDefaults.GEMINI_MAX_OUTPUT_TOKENS;

  /** 거절로 보는 finishReason (안전·차단 계열) */
  static final Set<String> BLOCKED_FINISH =
      Set.of(
          "SAFETY",
          "RECITATION",
          "BLOCKLIST",
          "PROHIBITED_CONTENT",
          "SPII",
          "IMAGE_SAFETY",
          "IMAGE_PROHIBITED_CONTENT");

  private final String id;
  private final String model;
  private final String apiKey;
  private final RestClient client;
  private final int maxOutputTokens;
  private final Double temperature;
  private final String schemaField;
  private final JsonMapper json;
  private final SuggestionParser parser;

  public GeminiAiProvider(
      String id,
      String model,
      String apiKey,
      String baseUrl,
      Duration timeout,
      Integer maxOutputTokens,
      Double temperature,
      String schemaField,
      JsonMapper json,
      SuggestionParser parser) {
    this.id = id;
    this.model = model;
    this.apiKey = apiKey;
    this.client = HttpCall.client(baseUrl == null ? DEFAULT_BASE_URL : baseUrl, timeout);
    this.maxOutputTokens = maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    this.temperature = temperature;
    this.schemaField = schemaField == null ? SCHEMA_RESPONSE_FORMAT : schemaField;
    this.json = json;
    this.parser = parser;
  }

  @Override
  public String name() {
    return id;
  }

  @Override
  public String model() {
    return model;
  }

  /** 모델 리소스 경로: "gemini-x" 또는 "models/gemini-x" 모두 받는다 */
  String modelPath() {
    return model.startsWith("models/") ? model : "models/" + model;
  }

  @Override
  public AiResult analyze(AiImage image, CodeSnapshot codes) {
    return toResult(send(requestBody(image, codes)), codes);
  }

  /** 이미지 없는 구조화 JSON: systemInstruction + user text, 스키마는 사진 분석과 같은 필드(schemaField)로 */
  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt) {
    JsonNode root = send(jsonRequestBody(prompt));
    Usage usage = usage(root);
    return new AiJsonResult(
        parser.parseObject(outputText(root)),
        root.path("modelVersion").asString(model),
        usage.input(),
        usage.output());
  }

  private JsonNode send(Map<String, Object> requestBody) {
    String body = json.writeValueAsString(requestBody);
    HttpCall.Result res =
        HttpCall.postJson(
            client,
            "/v1beta/" + modelPath() + ":generateContent",
            Map.of("x-goog-api-key", apiKey),
            body,
            "Gemini",
            apiKey);
    JsonNode root = readJson(res.body());
    if (!res.ok()) {
      String message = root == null ? res.body() : root.path("error").path("message").asString("");
      throw new AiProviderException(
          "API_" + res.status(), Secrets.upstream("Gemini", res.status(), message, apiKey));
    }
    if (root == null || !root.isObject()) {
      throw new AiProviderException("INVALID_RESPONSE", "Gemini 응답이 JSON이 아니에요.");
    }
    return root;
  }

  Map<String, Object> jsonRequestBody(AiJsonPrompt prompt) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("role", "user");
    content.put("parts", List.of(Map.of("text", prompt.prompt())));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.instructions()))));
    body.put("contents", List.of(content));
    body.put("generationConfig", generationConfig(prompt.schema()));
    return body;
  }

  private Map<String, Object> generationConfig(Map<String, Object> schema) {
    Map<String, Object> config = new LinkedHashMap<>();
    if (SCHEMA_RESPONSE_JSON_SCHEMA.equals(schemaField)) {
      config.put("responseMimeType", "application/json");
      config.put("responseJsonSchema", schema);
    } else {
      config.put(
          "responseFormat",
          Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema)));
    }
    config.put("maxOutputTokens", maxOutputTokens);
    if (temperature != null) {
      config.put("temperature", temperature);
    }
    return config;
  }

  Map<String, Object> requestBody(AiImage image, CodeSnapshot codes) {
    Map<String, Object> inlineData = new LinkedHashMap<>();
    inlineData.put("mimeType", image.mediaType());
    inlineData.put("data", Base64.getEncoder().encodeToString(image.bytes()));
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("role", "user");
    content.put(
        "parts",
        List.of(Map.of("inlineData", inlineData), Map.of("text", AnalysisPrompt.prompt(codes))));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("contents", List.of(content));
    body.put("generationConfig", generationConfig(AnalysisPrompt.schema(codes)));
    return body;
  }

  AiResult toResult(JsonNode root, CodeSnapshot codes) {
    AiSuggestion suggestion = parser.parse(outputText(root), codes);
    Usage usage = usage(root);
    return new AiResult(
        suggestion, root.path("modelVersion").asString(model), usage.input(), usage.output());
  }

  /** 차단·잘림을 걸러 내고 생각(thought)이 아닌 text를 이어 붙인다 */
  private static String outputText(JsonNode root) {
    String blockReason = root.path("promptFeedback").path("blockReason").asString("");
    if (!blockReason.isEmpty()) {
      throw new AiProviderException("REFUSAL", "Gemini가 요청을 차단했어요: " + blockReason);
    }
    JsonNode candidate = root.path("candidates").path(0);
    if (candidate.isMissingNode()) {
      throw new AiProviderException("EMPTY_OUTPUT", "Gemini 응답에 후보가 없어요.");
    }
    String finish = candidate.path("finishReason").asString("");
    if ("MAX_TOKENS".equals(finish)) {
      throw new AiProviderException("MAX_TOKENS", "Gemini 응답이 잘렸어요.");
    }
    if (BLOCKED_FINISH.contains(finish)) {
      throw new AiProviderException("REFUSAL", "Gemini가 응답을 멈췄어요: " + finish);
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode part : candidate.path("content").path("parts")) {
      if (part.path("thought").asBoolean(false)) {
        continue;
      }
      text.append(part.path("text").asString(""));
    }
    if (text.toString().isBlank()) {
      throw new AiProviderException(
          "EMPTY_OUTPUT", "Gemini 응답에 텍스트가 없어요 (finishReason=" + finish + ").");
    }
    return text.toString();
  }

  private record Usage(Long input, Long output) {}

  /** 입력 = promptTokenCount, 출력 = candidatesTokenCount + thoughtsTokenCount */
  private static Usage usage(JsonNode root) {
    JsonNode usage = root.path("usageMetadata");
    Long input = HttpCall.longOrNull(usage, "promptTokenCount");
    Long candidates = HttpCall.longOrNull(usage, "candidatesTokenCount");
    Long thoughts = HttpCall.longOrNull(usage, "thoughtsTokenCount");
    Long output =
        candidates == null && thoughts == null
            ? null
            : (candidates == null ? 0 : candidates) + (thoughts == null ? 0 : thoughts);
    return new Usage(input, output);
  }

  private JsonNode readJson(String body) {
    try {
      return body == null || body.isBlank() ? null : json.readTree(body);
    } catch (JacksonException e) {
      return null;
    }
  }
}
