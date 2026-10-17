package app.wearly.ai.domain;

/**
 * AI 호출 기록 한 줄 (tb_ai_analysis_log). 이미지는 남기지 않고 해시·크기·결과·메타만 둔다.
 * 성공한 ITEM_ANALYZE만 일일 한도에 센다.
 */
public record AiCallRecord(long userId, String feature, String provider, String model, String status, int latencyMs,
        Long inputTokens, Long outputTokens, String imageSha256, int imageBytes, AiSuggestion result, String errorCode) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String FEATURE_ITEM_ANALYZE = "ITEM_ANALYZE";
    /** 관리자 시험 분석. 일일 한도에 세지 않는다 */
    public static final String FEATURE_ADMIN_TEST = "ADMIN_TEST";

    static final int ERROR_CODE_MAX = 50;

    public static AiCallRecord success(long userId, String feature, String provider, AiResult result, int latencyMs,
            AiImage image) {
        return new AiCallRecord(userId, feature, provider, result.model(), SUCCESS, latencyMs, result.inputTokens(),
                result.outputTokens(), image.sha256(), image.bytes().length, result.suggestion(), null);
    }

    public static AiCallRecord failure(long userId, String feature, String provider, String model, String errorCode,
            int latencyMs, AiImage image) {
        String code = errorCode.length() > ERROR_CODE_MAX ? errorCode.substring(0, ERROR_CODE_MAX) : errorCode;
        return new AiCallRecord(userId, feature, provider, model, FAILED, latencyMs, null, null, image.sha256(),
                image.bytes().length, null, code);
    }
}
