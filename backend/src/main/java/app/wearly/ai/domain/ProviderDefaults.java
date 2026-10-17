package app.wearly.ai.domain;

/** 공급자 종류별 기본값 (options를 비워 두면 쓰는 값). GET /admin/ai/types에도 그대로 나간다 */
public final class ProviderDefaults {

  public static final String CLAUDE_BASE_URL = "https://api.anthropic.com";
  public static final int CLAUDE_MAX_TOKENS = 16000;
  public static final String CLAUDE_EFFORT = "medium";

  public static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
  public static final int OPENAI_MAX_OUTPUT_TOKENS = 16000;

  public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";
  public static final int GEMINI_MAX_OUTPUT_TOKENS = 8192;
  public static final String GEMINI_SCHEMA_RESPONSE_FORMAT = "responseFormat";
  public static final String GEMINI_SCHEMA_RESPONSE_JSON_SCHEMA = "responseJsonSchema";

  private ProviderDefaults() {}
}
