package app.wearly.admin.application.port.in;

import java.util.List;
import java.util.Map;

/** 관리자 API 응답 모양 (07 문서 4장). ID는 문자열, 시각은 ISO-8601 UTC */
public final class AdminViews {

    private AdminViews() {
    }

    public record AiToday(long total, long success, long failed) {
    }

    public record ProviderStatView(String providerId, long success, long failed, Long avgLatencyMs, long inputTokens,
            long outputTokens) {
    }

    public record DashboardView(long users, long newUsers7d, long items, AiToday aiToday,
            List<ProviderStatView> aiByProvider7d) {
    }

    public record AiLogView(String id, String createdAt, String userId, String userEmail, String feature, String providerId,
            String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens, Integer imageBytes,
            String resultSummary, String errorCode) {
    }

    public record AiLogDetailView(String id, String createdAt, String userId, String userEmail, String feature,
            String providerId, String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens,
            Integer imageBytes, String imageSha256, String errorCode, Map<String, Object> result) {
    }

    public record AuditView(String id, String adminUserId, String adminEmail, String action, String targetType,
            String targetId, Map<String, Object> before, Map<String, Object> after, String createdAt) {
    }

    /** aiToday = 오늘(한국 시간) 성공한 사진 분석 수 (한도에 세는 것과 같음) */
    public record AdminUserView(String id, String email, String name, String role, String status, String createdAt,
            long itemCount, long aiToday) {
    }

    public record AdminCodesView(String version, List<AdminGroupView> groups) {
    }

    public record AdminGroupView(String group, String name, String description, List<AdminCodeView> codes) {
    }

    /** usageCount = 이 코드를 쓰는 아이템 수 */
    public record AdminCodeView(String code, String label, String parent, int sortOrder, boolean active,
            Map<String, Object> attrs, long usageCount) {
    }

    public record Today(long success, long failed) {
    }

    /** 공급자 한 행. 키 값은 어디에도 없고 keyConfigured만 준다. usable = enabled + 모델 + 키 모두 갖춤 */
    public record ProviderView(String providerId, String type, String displayName, String model, String apiKeyEnv,
            boolean keyConfigured, boolean enabled, int priority, Map<String, Object> options, boolean usable, Today today) {
    }

    /** AI 기록 검색 조건 (쿼리 문자열 그대로) */
    public record AiLogSearch(String providerId, String status, String userEmail, String feature, String from, String to) {
    }
}
