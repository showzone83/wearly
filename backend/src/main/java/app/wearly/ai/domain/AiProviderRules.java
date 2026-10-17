package app.wearly.ai.domain;

import app.wearly.common.domain.FieldError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 관리자 공급자 입력 규칙 (07 문서 3·4장) */
public final class AiProviderRules {

  /** 영문 소문자로 시작하는 소문자·숫자·밑줄·하이픈 1~30자 */
  public static final Pattern PROVIDER_ID = Pattern.compile("^[a-z][a-z0-9_-]{0,29}$");

  public static final Pattern MODEL = Pattern.compile("^[A-Za-z0-9._:/@-]{1,100}$");

  /** 다른 서버 비밀(DB 비밀번호, JWT 키 등)을 공급자에게 보내지 않도록 키 환경변수 이름은 *_API_KEY 형식만 */
  public static final Pattern ENV_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,91}_API_KEY$");

  /** 목업 응답의 provider 값과 겹치므로 예약 */
  public static final String MOCK_ID = "mock";

  private AiProviderRules() {}

  public static String providerId(String raw, List<FieldError> errors) {
    String id = raw == null ? null : raw.strip();
    if (id == null || !PROVIDER_ID.matcher(id).matches()) {
      errors.add(new FieldError("providerId", "영문 소문자로 시작하는 소문자·숫자·밑줄·하이픈 1~30자여야 해요."));
    } else if (MOCK_ID.equals(id)) {
      errors.add(new FieldError("providerId", "mock은 목업 실행용 이름이라 쓸 수 없어요."));
    }
    return id;
  }

  public static AiProviderType type(String raw, List<FieldError> errors) {
    try {
      AiProviderType t = raw == null ? null : AiProviderType.valueOf(raw.strip());
      if (t == null) {
        errors.add(new FieldError("type", "CLAUDE, OPENAI, GEMINI 중 하나여야 해요."));
      }
      return t;
    } catch (IllegalArgumentException e) {
      errors.add(new FieldError("type", "CLAUDE, OPENAI, GEMINI 중 하나여야 해요."));
      return null;
    }
  }

  public static String displayName(String raw, List<FieldError> errors) {
    String v = raw == null ? null : raw.strip();
    if (v == null || v.isEmpty() || v.length() > 50) {
      errors.add(new FieldError("displayName", "1~50자로 입력해 주세요."));
    }
    return v;
  }

  /** 빈 값은 "모델 없음"(null) */
  public static String model(String raw, List<FieldError> errors) {
    String v = raw == null || raw.isBlank() ? null : raw.strip();
    if (v != null && !MODEL.matcher(v).matches()) {
      errors.add(new FieldError("model", "영문·숫자·. _ - : / @ 1~100자여야 해요."));
    }
    return v;
  }

  public static String apiKeyEnv(String raw, List<FieldError> errors) {
    String v = raw == null ? null : raw.strip();
    if (v == null || !ENV_NAME.matcher(v).matches()) {
      errors.add(new FieldError("apiKeyEnv", "영문 대문자·숫자·밑줄로 된 *_API_KEY 형식의 환경변수 이름이어야 해요."));
    }
    return v;
  }

  public static boolean validEnvName(String envName) {
    return envName != null && ENV_NAME.matcher(envName).matches();
  }

  /** null 값 옵션은 "기본값"이라 저장하지 않는다 */
  public static Map<String, Object> withoutNulls(Map<String, Object> options) {
    Map<String, Object> out = new LinkedHashMap<>();
    options.forEach(
        (k, v) -> {
          if (v != null) {
            out.put(k, v);
          }
        });
    return out;
  }
}
