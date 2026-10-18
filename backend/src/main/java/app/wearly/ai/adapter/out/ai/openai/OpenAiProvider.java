package app.wearly.ai.adapter.out.ai.openai;

import app.wearly.ai.adapter.out.ai.HttpCall;
import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.Secrets;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.ProviderDefaults;
import java.time.Duration;
import java.util.ArrayList;
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
 * input_image(data URL, detail, 사진이 있을 때)], 지시문은 instructions - 구조화 출력: text.format = { type:
 * json_schema, name, schema, strict: true } - 결과: output[].type=message →
 * content[].type=output_text의 text, refusal이면 거절 - 토큰: usage.input_tokens / usage.output_tokens, 캐시
 * 읽기는 usage.input_tokens_details.cached_tokens
 *
 * <p>프롬프트 캐싱: OpenAI는 1024토큰 이상인 프롬프트의 같은 앞부분을 자동으로 캐시한다 (요청에 따로 적을 것이 없다). 앞부분 순서는 구조화 출력 스키마 →
 * instructions(고정 지시문) → input(사용자 메시지·사진)이라 고정 부분이 앞에 온다.
 */
public class OpenAiProvider implements AiModelPort {

  public static final String DEFAULT_BASE_URL = ProviderDefaults.OPENAI_BASE_URL;
  static final int DEFAULT_MAX_OUTPUT_TOKENS = ProviderDefaults.OPENAI_MAX_OUTPUT_TOKENS;

  private final String id;
  private final String model;
  private final String apiKey;
  private final RestClient client;
  private final int maxOutputTokens;
  private final Double temperature;
  private final String detail;
  private final JsonMapper json;
  private final JsonObjectParser parser;

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
      JsonObjectParser parser) {
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

  /** 지시문은 instructions, 사용자 입력은 input_text(+ 사진이면 input_image), text.format = 호출한 쪽의 스키마 */
  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image) {
    JsonNode root = send(requestBody(prompt, image));
    JsonNode usage = root.path("usage");
    return new AiJsonResult(
        parser.parseObject(outputText(root)),
        root.path("model").asString(model),
        longOrNull(usage, "input_tokens"),
        longOrNull(usage, "output_tokens"),
        // 자동 캐시(1024토큰 이상 같은 앞부분)에서 읽은 입력. 쓰기 요금이 따로 없어 쓰기 토큰은 없다
        longOrNull(usage.path("input_tokens_details"), "cached_tokens"),
        null);
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

  Map<String, Object> requestBody(AiJsonPrompt prompt, AiImage image) {
    List<Map<String, Object>> content = new ArrayList<>();
    Map<String, Object> text = new LinkedHashMap<>();
    text.put("type", "input_text");
    text.put("text", prompt.prompt());
    content.add(text);
    if (image != null) {
      Map<String, Object> imagePart = new LinkedHashMap<>();
      imagePart.put("type", "input_image");
      imagePart.put(
          "image_url",
          "data:"
              + image.mediaType()
              + ";base64,"
              + Base64.getEncoder().encodeToString(image.bytes()));
      imagePart.put("detail", detail);
      content.add(imagePart);
    }
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", content);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    if (prompt.instructions() != null) {
      body.put("instructions", prompt.instructions());
    }
    body.put("input", List.of(message));
    body.put("text", Map.of("format", format(prompt.schemaName(), prompt.schema())));
    body.put("max_output_tokens", maxOutputTokens);
    if (temperature != null) {
      body.put("temperature", temperature);
    }
    // 입력·결과를 OpenAI 쪽에 30일 보관하지 않는다 (기본값 true)
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
