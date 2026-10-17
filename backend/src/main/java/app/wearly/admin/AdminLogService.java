package app.wearly.admin;

import app.wearly.admin.AdminQueryMapper.AiLogFilter;
import app.wearly.admin.AdminRows.AiCounts;
import app.wearly.admin.AdminRows.AiLogRow;
import app.wearly.admin.AdminRows.AuditRow;
import app.wearly.admin.AdminRows.Counts;
import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.common.error.ApiException;
import app.wearly.config.ClockConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드·AI 분석 기록·변경 이력 조회 (MyBatis, 읽기 전용) */
@Service
public class AdminLogService {

    static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");
    static final Set<String> FEATURES = Set.of("ITEM_ANALYZE", "ADMIN_TEST");
    static final Set<String> TARGET_TYPES = Set.of(AuditService.CODE, AuditService.AI_PROVIDER, AuditService.USER);

    private final AdminQueryMapper queries;
    private final Clock clock;

    public AdminLogService(AdminQueryMapper queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public record AiToday(long total, long success, long failed) {
    }

    public record ProviderStatDto(String providerId, long success, long failed, Long avgLatencyMs, long inputTokens,
            long outputTokens) {
    }

    public record DashboardDto(long users, long newUsers7d, long items, AiToday aiToday,
            List<ProviderStatDto> aiByProvider7d) {
    }

    public record AiLogDto(String id, String createdAt, String userId, String userEmail, String feature, String providerId,
            String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens, Integer imageBytes,
            String resultSummary, String errorCode) {
    }

    public record AiLogDetailDto(String id, String createdAt, String userId, String userEmail, String feature,
            String providerId, String model, String status, int latencyMs, Integer inputTokens, Integer outputTokens,
            Integer imageBytes, String imageSha256, String errorCode, Map<String, Object> result) {
    }

    public record AuditDto(String id, String adminUserId, String adminEmail, String action, String targetType,
            String targetId, Map<String, Object> before, Map<String, Object> after, String createdAt) {
    }

    /** newUsers7d·aiByProvider7d는 지금부터 7일 전까지, aiToday는 오늘(한국 시간) */
    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        Instant now = clock.instant();
        Instant weekAgo = now.minus(Duration.ofDays(7));
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Counts c = queries.counts(weekAgo);
        AiCounts ai = queries.aiCounts(today.atStartOfDay(ClockConfig.SEOUL).toInstant(),
                today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant());
        List<ProviderStatDto> byProvider = queries.providerStats(weekAgo, now.plusSeconds(1)).stream()
                .map(r -> new ProviderStatDto(r.providerId, r.success, r.failed, r.avgLatencyMs,
                        r.inputTokens == null ? 0 : r.inputTokens, r.outputTokens == null ? 0 : r.outputTokens))
                .toList();
        return new DashboardDto(c.users, c.newUsers7d, c.items, new AiToday(ai.total, ai.success, ai.failed), byProvider);
    }

    /**
     * from·to: YYYY-MM-DD(한국 시간, 둘 다 그날 포함) 또는 ISO-8601 시각(from 포함, to 미포함).
     * userEmail은 부분 일치(대소문자 무시).
     */
    @Transactional(readOnly = true)
    public PageResponse<AiLogDto> aiLogs(String providerId, String status, String userEmail, String feature, String from,
            String to, PageResponse.Paging paging) {
        String st = blankToNull(status);
        if (st != null && !STATUSES.contains(st)) {
            throw ApiException.validation("status", "SUCCESS 또는 FAILED여야 해요.");
        }
        String ft = blankToNull(feature);
        if (ft != null && !FEATURES.contains(ft)) {
            throw ApiException.validation("feature", "ITEM_ANALYZE 또는 ADMIN_TEST여야 해요.");
        }
        String email = blankToNull(userEmail);
        AiLogFilter filter = new AiLogFilter(blankToNull(providerId), st,
                email == null ? null : "%" + escapeLike(email.strip()) + "%", ft, time("from", from, false),
                time("to", to, true));
        List<AiLogDto> items = queries.findAiLogs(filter, paging.size(), paging.offset()).stream()
                .map(AdminLogService::toDto).toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countAiLogs(filter));
    }

    @Transactional(readOnly = true)
    public AiLogDetailDto aiLog(String id) {
        AiLogRow r = queries.findAiLog(Ids.parse(id));
        if (r == null) {
            throw ApiException.notFound();
        }
        return new AiLogDetailDto(Ids.format(r.id), Times.iso(r.createdAt), Ids.format(r.userId), r.userEmail, r.feature,
                r.providerId, r.model, r.status, r.latencyMs, r.inputTokens, r.outputTokens, r.imageBytes, r.imageSha256,
                r.errorCode, r.result);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDto> auditLogs(String targetType, PageResponse.Paging paging) {
        String tt = blankToNull(targetType);
        if (tt != null && !TARGET_TYPES.contains(tt)) {
            throw ApiException.validation("targetType", "CODE, AI_PROVIDER, USER 중 하나여야 해요.");
        }
        List<AuditDto> items = queries.findAuditLogs(tt, paging.size(), paging.offset()).stream()
                .map(AdminLogService::toDto).toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countAuditLogs(tt));
    }

    private static AiLogDto toDto(AiLogRow r) {
        return new AiLogDto(Ids.format(r.id), Times.iso(r.createdAt), Ids.format(r.userId), r.userEmail, r.feature,
                r.providerId, r.model, r.status, r.latencyMs, r.inputTokens, r.outputTokens, r.imageBytes, summary(r.result),
                r.errorCode);
    }

    private static AuditDto toDto(AuditRow r) {
        return new AuditDto(Ids.format(r.id), Ids.format(r.adminUserId), r.adminEmail, r.action, r.targetType, r.targetId,
                r.before, r.after, Times.iso(r.createdAt));
    }

    /** 결과 요약: "네이비 울 니트 · TOP/KNIT · navy" */
    static String summary(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        return Objects.toString(result.get("name"), "") + " · " + result.get("category") + "/" + result.get("subType")
                + " · " + result.get("color");
    }

    private Instant time(String field, String raw, boolean end) {
        String v = blankToNull(raw);
        if (v == null) {
            return null;
        }
        try {
            if (v.length() == 10) {
                LocalDate d = LocalDate.parse(v);
                return (end ? d.plusDays(1) : d).atStartOfDay(ClockConfig.SEOUL).toInstant();
            }
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, "YYYY-MM-DD 또는 ISO-8601 시각이어야 해요.");
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
