package app.wearly.ai.adapter.out.ai.openai;

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
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 공급자: Responses API(POST {baseUrl}/responses)를 RestClient로 직접 부른다. 요청 형식은 OpenAI 공식 OpenAPI
 * 명세(openai/openai-openapi)의 CreateResponse를 따른다. - 입력: user 메시지 content = [input_text(프롬프트),
 * input_image(data URL, detail)] - 구조화 출력: text.format = { type: json_schema, name, schema, strict:
 * true } - 결과: output[].type=message → content[].type=output_text의 text, refusal이면 거절 - 토큰:
 * usage.input_tokens / usage.output_tokens
 */
public class OpenAiProvider implements AiModelPort {

  public static final String DEFAULT_BASE_URL = ProviderDefaults.OPENAI_BASE_URL;
  static final String SCHEMA_NAME = "wardrobe_item";
  static final int DEFAULT_MAX_OUTPUT_TOKENS = ProviderDefaults.OPENAI_MAX_OUTPUT_TOKENS;

  private final String id;
  private final String model;
  private final String apiKey;
  private final RestClient client;
  private final int maxOutputTokens;
  private final Double temperature;
  private final String detail;
  private final JsonMapper json;
  private final SuggestionParser parser;

  public OpenAiProvider(
      String id,
      String model,
      String apiKey,
      String baseUrl,
      Duration timeout,
      Integer maxOutputTokens,
      Double temperature,
      String detail,
      JsonMapper json,
      SuggestionParser parser) {
    this.id = id;
    this.model = model;
    this.apiKey = apiKey;
    this.client = HttpCall.client(baseUrl == null ? DEFAULT_BASE_URL : baseUrl, timeout);
    this.maxOutputTokens = maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    this.temperature = temperature;
    this.detail = detail == null ? "auto" : detail;
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

  @Override
  public AiResult analyze(AiImage image, CodeSnapshot codes) {
    return toResult(send(requestBody(image, codes)), codes);
  }

  /** 이미지 없는 구조화 JSON: instructions + user input_text, text.format = 호출한 쪽의 스키마 */
  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt) {
    JsonNode root = send(jsonRequestBody(prompt));
    JsonNode usage = root.path("usage");
    return new AiJsonResult(
        parser.parseObject(outputText(root)),
        root.path("model").asString(model),
        longOrNull(usage, "input_tokens"),
        longOrNull(usage, "output_tokens"));
  }

  private JsonNode send(Map<String, Object> requestBody) {
    String body = json.writeValueAsString(requestBody);
    HttpCall.Result res =
        HttpCall.postJson(
            client,
            "/responses",
            Map.of("Authorization", "Bearer " + apiKey),
            body,
            "OpenAI",
            apiKey);
    JsonNode root = readJson(res.body());
    if (!res.ok()) {
      String message = root == null ? res.body() : root.path("error").path("message").asString("");
      throw new AiProviderException(
          "API_" + res.status(), Secrets.upstream("OpenAI", res.status(), message, apiKey));
    }
    if (root == null || !root.isObject()) {
      throw new AiProviderException("INVALID_RESPONSE", "OpenAI 응답이 JSON이 아니에요.");
    }
    return root;
  }

  Map<String, Object> jsonRequestBody(AiJsonPrompt prompt) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", List.of(Map.of("type", "input_text", "text", prompt.prompt())));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("instructions", prompt.instructions());
    body.put("input", List.of(message));
    body.put("text", Map.of("format", format(prompt.schemaName(), prompt.schema())));
    body.put("max_output_tokens", maxOutputTokens);
    if (temperature != null) {
      body.put("temperature", temperature);
    }
    body.put("store", false);
    return body;
  }

  private static Map<String, Object> format(String name, Map<String, Object> schema) {
    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", name);
    format.put("schema", schema);
    format.put("strict", true);
    return format;
  }

  Map<String, Object> requestBody(AiImage image, CodeSnapshot codes) {
    String dataUrl =
        "data:"
            + image.mediaType()
            + ";base64,"
            + Base64.getEncoder().encodeToString(image.bytes());
    Map<String, Object> imagePart = new LinkedHashMap<>();
    imagePart.put("type", "input_image");
    imagePart.put("image_url", dataUrl);
    imagePart.put("detail", detail);
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put(
        "content",
        List.of(Map.of("type", "input_text", "text", AnalysisPrompt.prompt(codes)), imagePart));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("input", List.of(message));
    body.put("text", Map.of("format", format(SCHEMA_NAME, AnalysisPrompt.schema(codes))));
    body.put("max_output_tokens", maxOutputTokens);
    if (temperature != null) {
      body.put("temperature", temperature);
    }
    // 분석 결과를 OpenAI 쪽에 30일 보관하지 않는다 (기본값 true)
    body.put("store", false);
    return body;
  }

  AiResult toResult(JsonNode root, CodeSnapshot codes) {
    AiSuggestion suggestion = parser.parse(outputText(root), codes);
    JsonNode usage = root.path("usage");
    return new AiResult(
        suggestion,
        root.path("model").asString(model),
        longOrNull(usage, "input_tokens"),
        longOrNull(usage, "output_tokens"));
  }

  /** 미완료·실패·거절을 걸러 내고 output_text를 이어 붙인다 */
  private String outputText(JsonNode root) {
    String status = root.path("status").asString("");
    if ("incomplete".equals(status)) {
      String reason = root.path("incomplete_details").path("reason").asString("");
      if ("max_output_tokens".equals(reason)) {
        throw new AiProviderException("MAX_TOKENS", "OpenAI 응답이 잘렸어요.");
      }
      if ("content_filter".equals(reason)) {
        throw new AiProviderException("REFUSAL", "OpenAI가 콘텐츠 필터로 응답을 멈췄어요.");
      }
      throw new AiProviderException("INCOMPLETE", "OpenAI 응답이 완료되지 않았어요: " + reason);
    }
    if (!"completed".equals(status)) {
      String message = root.path("error").path("message").asString(status);
      throw new AiProviderException(
          "API_FAILED", "OpenAI 응답 실패: " + Secrets.scrub(message, apiKey));
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode item : root.path("output")) {
      if (!"message".equals(item.path("type").asString(""))) {
        continue; // reasoning 등
      }
      for (JsonNode part : item.path("content")) {
        String type = part.path("type").asString("");
        if ("refusal".equals(type)) {
          throw new AiProviderException("REFUSAL", "OpenAI가 분석을 거절했어요.");
        }
        if ("output_text".equals(type)) {
          text.append(part.path("text").asString(""));
        }
      }
    }
    if (text.isEmpty() || text.toString().isBlank()) {
      throw new AiProviderException("EMPTY_OUTPUT", "OpenAI 응답에 텍스트가 없어요.");
    }
    return text.toString();
  }

  private JsonNode readJson(String body) {
    try {
      return body == null || body.isBlank() ? null : json.readTree(body);
    } catch (JacksonException e) {
      return null;
    }
  }

  static Long longOrNull(JsonNode node, String field) {
    return HttpCall.longOrNull(node, field);
  }
}
