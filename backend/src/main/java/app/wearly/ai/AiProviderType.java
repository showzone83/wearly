package app.wearly.ai;

import app.wearly.common.error.ErrorResponse.FieldError;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공급자 구현 종류와 각 종류가 받는 options (관리자 화면 폼과 검증에 쓴다).
 * MOCK은 DB 행으로 두지 않는다(관리자 API로 만들 수 없음). 직접 넣은 행이 있으면 목업으로 동작한다.
 */
public enum AiProviderType {
    CLAUDE("Claude (Anthropic Messages API)", "ANTHROPIC_API_KEY", List.of(
            OptionSpec.ofEnum("effort", "생각 깊이 (output_config.effort)", "medium",
                    List.of("low", "medium", "high", "xhigh", "max")),
            OptionSpec.ofInt("maxTokens", "최대 출력 토큰 (max_tokens)", 16000, 1, 128000),
            OptionSpec.ofInt("timeoutSeconds", "호출 제한 시간(초). 비우면 WEARLY_AI_TIMEOUT", null, 1, 600),
            OptionSpec.ofUrl("baseUrl", "API 주소 (테스트·프록시용)", "https://api.anthropic.com"))),
    OPENAI("OpenAI (Responses API)", "OPENAI_API_KEY", List.of(
            OptionSpec.ofNumber("temperature", "샘플링 온도 0~2. 비우면 모델 기본값 (추론 모델은 받지 않을 수 있다)", null, 0, 2),
            OptionSpec.ofInt("maxTokens", "최대 출력 토큰 (max_output_tokens, 추론 토큰 포함)", 16000, 16, 128000),
            OptionSpec.ofInt("timeoutSeconds", "호출 제한 시간(초). 비우면 WEARLY_AI_TIMEOUT", null, 1, 600),
            OptionSpec.ofUrl("baseUrl", "API 주소 (테스트·프록시용)", "https://api.openai.com/v1"))),
    GEMINI("Gemini (Gemini API generateContent)", "GEMINI_API_KEY", List.of(
            OptionSpec.ofNumber("temperature", "샘플링 온도 0~2. 비우면 모델 기본값", null, 0, 2),
            OptionSpec.ofInt("maxTokens", "최대 출력 토큰 (generationConfig.maxOutputTokens)", 16000, 1, 128000),
            OptionSpec.ofInt("timeoutSeconds", "호출 제한 시간(초). 비우면 WEARLY_AI_TIMEOUT", null, 1, 600),
            OptionSpec.ofUrl("baseUrl", "API 주소 (테스트·프록시용)", "https://generativelanguage.googleapis.com/v1beta"))),
    MOCK("목업 (같은 사진 = 같은 결과)", "", List.of());

    /** 관리자 API로 만들 수 있는 종류 */
    public static final List<AiProviderType> CREATABLE = List.of(CLAUDE, OPENAI, GEMINI);

    private final String displayName;
    private final String defaultApiKeyEnv;
    private final List<OptionSpec> options;

    AiProviderType(String displayName, String defaultApiKeyEnv, List<OptionSpec> options) {
        this.displayName = displayName;
        this.defaultApiKeyEnv = defaultApiKeyEnv;
        this.options = options;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultApiKeyEnv() {
        return defaultApiKeyEnv;
    }

    public List<OptionSpec> options() {
        return options;
    }

    /** 모르는 키, 형식·범위가 틀린 값은 fieldErrors(options.키)로 돌려준다. null 값은 '기본값 사용'으로 보고 지운다. */
    public Map<String, Object> normalizeOptions(Map<String, Object> raw, List<FieldError> errors) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String field = "options." + e.getKey();
            OptionSpec spec = options.stream().filter(o -> o.key().equals(e.getKey())).findFirst().orElse(null);
            if (spec == null) {
                errors.add(new FieldError(field, "이 종류에서 쓰지 않는 옵션이에요."));
                continue;
            }
            if (e.getValue() == null) {
                continue;
            }
            Object v = spec.check(e.getValue());
            if (v == null) {
                errors.add(new FieldError(field, spec.rule()));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    /** 화면 폼용 옵션 설명 */
    public record OptionSpec(String key, String type, String description, Object defaultValue, Number min, Number max,
            List<String> values) {

        static OptionSpec ofEnum(String key, String description, String def, List<String> values) {
            return new OptionSpec(key, "enum", description, def, null, null, values);
        }

        static OptionSpec ofInt(String key, String description, Integer def, int min, int max) {
            return new OptionSpec(key, "integer", description, def, min, max, null);
        }

        static OptionSpec ofNumber(String key, String description, Double def, double min, double max) {
            return new OptionSpec(key, "number", description, def, min, max, null);
        }

        static OptionSpec ofUrl(String key, String description, String def) {
            return new OptionSpec(key, "url", description, def, null, null, null);
        }

        String rule() {
            return switch (type) {
                case "enum" -> String.join(", ", values) + " 중 하나여야 해요.";
                case "integer" -> min + "~" + max + " 정수여야 해요.";
                case "number" -> min + "~" + max + " 숫자여야 해요.";
                default -> "http(s) 주소여야 해요.";
            };
        }

        /** 맞으면 정규화한 값, 틀리면 null */
        Object check(Object value) {
            switch (type) {
                case "enum":
                    return value instanceof String s && values.contains(s) ? s : null;
                case "integer":
                    if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                            && n.doubleValue() >= min.doubleValue() && n.doubleValue() <= max.doubleValue()) {
                        return n.intValue();
                    }
                    return null;
                case "number":
                    if (value instanceof Number n && n.doubleValue() >= min.doubleValue()
                            && n.doubleValue() <= max.doubleValue()) {
                        return n.doubleValue();
                    }
                    return null;
                default:
                    if (value instanceof String s && validUrl(s)) {
                        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
                    }
                    return null;
            }
        }

        private static boolean validUrl(String s) {
            try {
                URI uri = URI.create(s);
                return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                        && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
