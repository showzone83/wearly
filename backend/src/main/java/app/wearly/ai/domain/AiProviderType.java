package app.wearly.ai.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 공급자 구현 종류와 options 규칙. MOCK은 목업 실행 전용이라 행으로 만들 수 없다 */
public enum AiProviderType {
  CLAUDE(
      "Anthropic Claude (Messages API, Anthropic Java SDK)",
      List.of(
          OptionSpec.oneOf(
              "effort",
              "output_config.effort (생각 깊이·토큰 사용량)",
              "medium",
              List.of("low", "medium", "high", "xhigh", "max")),
          OptionSpec.integer(
              "maxTokens", "max_tokens", ProviderDefaults.CLAUDE_MAX_TOKENS, 1, 128000),
          OptionSpec.integer("timeoutSeconds", "호출 제한 시간(초). 없으면 WEARLY_AI_TIMEOUT", null, 1, 600),
          OptionSpec.url(
              "baseUrl",
              "API 주소 (테스트·프록시용, WEARLY_AI_ALLOW_BASE_URL=true일 때만)",
              ProviderDefaults.CLAUDE_BASE_URL))),
  OPENAI(
      "OpenAI (Responses API, REST)",
      List.of(
          OptionSpec.number(
              "temperature", "temperature 0~2 (추론 모델은 지원하지 않을 수 있어 기본은 보내지 않음)", null, 0, 2),
          OptionSpec.integer(
              "maxTokens",
              "max_output_tokens (추론 토큰 포함)",
              ProviderDefaults.OPENAI_MAX_OUTPUT_TOKENS,
              16,
              128000),
          OptionSpec.oneOf(
              "detail",
              "input_image.detail (이미지 해상도)",
              "auto",
              List.of("low", "high", "auto", "original")),
          OptionSpec.integer("timeoutSeconds", "호출 제한 시간(초). 없으면 WEARLY_AI_TIMEOUT", null, 1, 600),
          OptionSpec.url(
              "baseUrl",
              "API 주소 (테스트·프록시용, WEARLY_AI_ALLOW_BASE_URL=true일 때만)",
              ProviderDefaults.OPENAI_BASE_URL))),
  GEMINI(
      "Google Gemini (generateContent, REST)",
      List.of(
          OptionSpec.number(
              "temperature", "generationConfig.temperature 0~2 (일부 최신 모델은 무시)", null, 0, 2),
          OptionSpec.integer(
              "maxTokens",
              "generationConfig.maxOutputTokens",
              ProviderDefaults.GEMINI_MAX_OUTPUT_TOKENS,
              1,
              128000),
          OptionSpec.oneOf(
              "schemaField",
              "구조화 출력 필드: responseFormat(현재 권장) 또는 responseJsonSchema",
              ProviderDefaults.GEMINI_SCHEMA_RESPONSE_FORMAT,
              List.of(
                  ProviderDefaults.GEMINI_SCHEMA_RESPONSE_FORMAT,
                  ProviderDefaults.GEMINI_SCHEMA_RESPONSE_JSON_SCHEMA)),
          OptionSpec.integer("timeoutSeconds", "호출 제한 시간(초). 없으면 WEARLY_AI_TIMEOUT", null, 1, 600),
          OptionSpec.url(
              "baseUrl",
              "API 주소 (테스트·프록시용, WEARLY_AI_ALLOW_BASE_URL=true일 때만)",
              ProviderDefaults.GEMINI_BASE_URL)));

  private final String description;
  private final List<OptionSpec> options;

  AiProviderType(String description, List<OptionSpec> options) {
    this.description = description;
    this.options = options;
  }

  public String description() {
    return description;
  }

  public List<OptionSpec> options() {
    return options;
  }

  /** options 검증. 모르는 키·형식 오류는 options.키 필드 오류로 */
  public List<FieldError> validate(Map<String, Object> values, boolean allowBaseUrl) {
    List<FieldError> errors = new ArrayList<>();
    if (values == null) {
      return errors;
    }
    for (Map.Entry<String, Object> e : values.entrySet()) {
      String field = "options." + e.getKey();
      OptionSpec spec =
          options.stream().filter(o -> o.name().equals(e.getKey())).findFirst().orElse(null);
      if (spec == null) {
        errors.add(FieldError.notAllowed(field));
        continue;
      }
      Object v = e.getValue();
      if (v == null) {
        continue; // null은 기본값을 쓴다는 뜻
      }
      switch (spec.kind()) {
        case OptionSpec.INTEGER -> {
          if (!(v instanceof Integer || v instanceof Long)
              || outOfRange(((Number) v).doubleValue(), spec)) {
            errors.add(
                v instanceof Integer || v instanceof Long
                    ? FieldError.outOfRange(field, spec.min(), spec.max())
                    : FieldError.invalidType(field, "integer"));
          }
        }
        case OptionSpec.NUMBER -> {
          if (!(v instanceof Number n) || outOfRange(n.doubleValue(), spec)) {
            errors.add(
                v instanceof Number
                    ? FieldError.outOfRange(field, spec.min(), spec.max())
                    : FieldError.invalidType(field, "number"));
          }
        }
        case OptionSpec.ENUM -> {
          if (!(v instanceof String s) || !spec.values().contains(s)) {
            errors.add(FieldError.notAllowed(field, spec.values()));
          }
        }
        case OptionSpec.URL -> {
          if (!allowBaseUrl) {
            // API 주소 변경은 서버 설정(wearly.ai.allow-base-url)을 켰을 때만
            errors.add(new FieldError(field, FieldErrorCode.DISABLED_BY_SERVER));
          } else if (!(v instanceof String s) || !isHttpUrl(s)) {
            errors.add(FieldError.invalidFormat(field, "url"));
          }
        }
        default -> {
          if (!(v instanceof String)) {
            errors.add(FieldError.invalidType(field, "string"));
          }
        }
      }
    }
    return errors;
  }

  private static boolean outOfRange(double v, OptionSpec spec) {
    return Double.isNaN(v) || v < spec.min().doubleValue() || v > spec.max().doubleValue();
  }

  private static boolean isHttpUrl(String s) {
    try {
      URI uri = URI.create(s);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && uri.getQuery() == null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
