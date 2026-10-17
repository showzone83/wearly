package app.wearly.ai.domain;

import java.util.regex.Pattern;

/**
 * AI 호출 기록 한 줄 (tb_ai_analysis_log). 이미지는 남기지 않고 해시·크기·결과·메타만 둔다. 이미지 없는 호출(코디 추천)은 이미지 칸이 null이다.
 * 일일 한도는 기능(feature)별로 성공한 호출만 센다.
 */
public record AiCallRecord(
    long userId,
    String feature,
    String provider,
    String model,
    String status,
    int latencyMs,
    Long inputTokens,
    Long outputTokens,
    String imageSha256,
    Integer imageBytes,
    Object result,
    String summary,
    String errorCode) {

  public static final String SUCCESS = "SUCCESS";
  public static final String FAILED = "FAILED";
  public static final String FEATURE_ITEM_ANALYZE = "ITEM_ANALYZE";

  /** 기능 이름 형식 (V7 CHECK와 같다). 사진 분석·시험 분석 밖의 기능 이름은 호출하는 컨텍스트가 정한다 (L6) */
  public static final Pattern FEATURE_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

  /** 관리자 시험 분석. 일일 한도에 세지 않는다 */
  public static final String FEATURE_ADMIN_TEST = "ADMIN_TEST";

  static final int ERROR_CODE_MAX = 50;

  public static AiCallRecord failure(
      long userId,
      String feature,
      String provider,
      String model,
      String errorCode,
      int latencyMs,
      AiImage image) {
    return new AiCallRecord(
        userId,
        feature,
        provider,
        model,
        FAILED,
        latencyMs,
        null,
        null,
        image.sha256(),
        image.bytes().length,
        null,
        null,
        cut(errorCode));
  }

  /** 이미지 없는 구조화 JSON 호출의 실패 기록. 응답을 받았으면 토큰 수를 남긴다 */
  public static AiCallRecord jsonFailure(
      long userId,
      String feature,
      String provider,
      String model,
      String errorCode,
      int latencyMs,
      AiJsonResult received) {
    return new AiCallRecord(
        userId,
        feature,
        provider,
        received == null ? model : received.model(),
        FAILED,
        latencyMs,
        received == null ? null : received.inputTokens(),
        received == null ? null : received.outputTokens(),
        null,
        null,
        null,
        null,
        cut(errorCode));
  }

  private static String cut(String errorCode) {
    return errorCode.length() > ERROR_CODE_MAX ? errorCode.substring(0, ERROR_CODE_MAX) : errorCode;
  }
}
