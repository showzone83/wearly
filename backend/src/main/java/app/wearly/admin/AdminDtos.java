package app.wearly.admin;

import app.wearly.ai.AiProviderType.OptionSpec;
import app.wearly.ai.AiSuggestion;
import java.util.List;
import java.util.Map;

/** 관리자 API 요청·응답 (doc/07_관리자_사이트.md 4장). ID는 문자열, 시각은 ISO-8601 UTC 문자열 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 목록 응답 공통 모양 */
    public record PageResponse<T>(List<T> items, int page, int size, long total) {
    }

    // 대시보드
    public record Dashboard(long users, long newUsers7d, long items, AiToday aiToday, List<ProviderStat7d> aiByProvider7d) {
    }

    public record AiToday(long total, long success, long failed) {
    }

    public record ProviderStat7d(String providerId, long success, long failed, Long avgLatencyMs, long inputTokens,
            long outputTokens) {
    }

    // 코드
    public record CodeCreateRequest(String code, String label, String parent, Integer sortOrder, Boolean active,
            Map<String, Object> attrs) {
    }

    public record CodeOrderRequest(List<String> codes) {
    }

    // AI 공급자
    public record ProviderDto(String providerId, String type, String displayName, String model, String apiKeyEnv,
            boolean keyConfigured, boolean enabled, int priority, Map<String, Object> options, boolean usable,
            TodayCount today) {
    }

    public record TodayCount(long success, long failed) {
    }

    public record ProviderCreateRequest(String providerId, String type, String displayName, String model,
            String apiKeyEnv, Boolean enabled, Integer priority, Map<String, Object> options) {
    }

    public record ProviderTestResult(boolean ok, AiSuggestion suggestion, String errorCode, String errorMessage,
            int latencyMs, Long inputTokens, Long outputTokens, String model) {
    }

    public record ProviderTypeInfo(String type, String displayName, String defaultApiKeyEnv, List<OptionSpec> options) {
    }

    // AI 분석 기록
    public record AiLogItem(String id, String createdAt, String userId, String userEmail, String feature,
            String providerId, String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens,
            Integer imageBytes, String resultSummary, String errorCode) {
    }

    public record AiLogDetail(String id, String createdAt, String userId, String userEmail, String feature,
            String providerId, String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens,
            Integer imageBytes, String imageSha256, String errorCode, Map<String, Object> result) {
    }

    // 회원
    public record UserItem(String id, String email, String name, String role, String status, String createdAt,
            long itemCount, long aiToday) {
    }

    // 변경 이력
    public record AuditItem(String id, String adminUserId, String adminEmail, String action, String targetType,
            String targetId, Map<String, Object> before, Map<String, Object> after, String createdAt) {
    }
}
