package app.wearly.admin;

import app.wearly.admin.AdminDtos.AiLogDetail;
import app.wearly.admin.AdminDtos.AiLogItem;
import app.wearly.admin.AdminDtos.AiToday;
import app.wearly.admin.AdminDtos.AuditItem;
import app.wearly.admin.AdminDtos.Dashboard;
import app.wearly.admin.AdminDtos.PageResponse;
import app.wearly.admin.AdminDtos.ProviderStat7d;
import app.wearly.admin.AdminQueryMapper.AiLogFilter;
import app.wearly.admin.AdminRows.AiCounts;
import app.wearly.admin.AdminRows.AiLogRow;
import app.wearly.ai.AiAnalysisLog;
import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import app.wearly.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 대시보드·AI 분석 기록·변경 이력 조회 (모두 MyBatis 읽기). 날짜 기준은 Asia/Seoul */
@Service
public class AdminReportService {

    static final Set<String> AI_STATUSES = Set.of(AiAnalysisLog.SUCCESS, AiAnalysisLog.FAILED);
    static final Set<String> FEATURES = Set.of(AiAnalysisLog.ITEM_ANALYZE, AiAnalysisLog.ADMIN_TEST);
    static final Set<String> TARGET_TYPES = Set.of(AuditService.CODE, AuditService.AI_PROVIDER, AuditService.USER);

    private final AdminQueryMapper queries;
    private final Clock clock;

    public AdminReportService(AdminQueryMapper queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /** 오늘 AI 호출과 공급자별 7일 통계는 모든 기능(회원 분석 + 관리자 시험 분석)을 센다 (비용을 보려는 것) */
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant todayFrom = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant tomorrow = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant weekFrom = today.minusDays(6).atStartOfDay(ClockConfig.SEOUL).toInstant();
        AiCounts c = queries.aiCounts(todayFrom, tomorrow);
        List<ProviderStat7d> byProvider = queries.providerStats(weekFrom, tomorrow).stream()
                .map(s -> new ProviderStat7d(s.providerId, s.success, s.failed,
                        s.avgLatencyMs == null ? null : Math.round(s.avgLatencyMs), s.inputTokens, s.outputTokens))
                .toList();
        return new Dashboard(queries.countUsers(), queries.countUsersSince(weekFrom), queries.countItems(),
                new AiToday(c.total, c.success, c.failed), byProvider);
    }

    /**
     * from·to는 YYYY-MM-DD(한국 날짜, to는 그날 끝까지) 또는 ISO-8601 시각.
     * userEmail은 부분 일치(대소문자 무시).
     */
    @Transactional(readOnly = true)
    public PageResponse<AiLogItem> aiLogs(String providerId, String status, String feature, String userEmail,
            String from, String to, Paging paging) {
        List<FieldError> errors = new ArrayList<>();
        String st = optionalOf(status, AI_STATUSES, "status", errors);
        String fe = optionalOf(feature, FEATURES, "feature", errors);
        Instant fromAt = parseTime(from, "from", false, errors);
        Instant toAt = parseTime(to, "to", true, errors);
        throwIfAny(errors);
        String pid = providerId == null || providerId.isBlank() ? null : providerId.strip();
        String email = userEmail == null || userEmail.isBlank() ? null
                : "%" + AdminUserService.escapeLike(userEmail.strip()) + "%";
        AiLogFilter filter = new AiLogFilter(pid, st, fe, email, fromAt, toAt);
        List<AiLogItem> items = queries.findAiLogs(filter, paging.offset(), paging.size()).stream()
                .map(r -> new AiLogItem(Ids.format(r.id), Times.iso(r.createdAt), Ids.format(r.userId), r.userEmail,
                        r.feature, r.providerId, r.model, r.status, r.latencyMs, r.inputTokens, r.outputTokens,
                        r.imageBytes, summary(r.result), r.errorCode))
                .toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countAiLogs(filter));
    }

    @Transactional(readOnly = true)
    public AiLogDetail aiLog(long id) {
        AiLogRow r = queries.findAiLog(id);
        if (r == null) {
            throw ApiException.notFound();
        }
        return new AiLogDetail(Ids.format(r.id), Times.iso(r.createdAt), Ids.format(r.userId), r.userEmail, r.feature,
                r.providerId, r.model, r.status, r.latencyMs, r.inputTokens, r.outputTokens, r.imageBytes, r.imageSha256,
                r.errorCode, r.result);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditItem> auditLogs(String targetType, Paging paging) {
        List<FieldError> errors = new ArrayList<>();
        String tt = optionalOf(targetType, TARGET_TYPES, "targetType", errors);
        throwIfAny(errors);
        List<AuditItem> items = queries.findAuditLogs(tt, paging.offset(), paging.size()).stream()
                .map(a -> new AuditItem(Ids.format(a.id), Ids.format(a.adminUserId), a.adminEmail, a.action, a.targetType,
                        a.targetId, a.before, a.after, Times.iso(a.createdAt)))
                .toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countAuditLogs(tt));
    }

    /** 결과 요약: "네이비 울 니트 (TOP/KNIT, navy)" */
    static String summary(Map<String, Object> result) {
        if (result == null || result.get("name") == null) {
            return null;
        }
        return result.get("name") + " (" + result.get("category") + "/" + result.get("subType") + ", "
                + result.get("color") + ")";
    }

    private static Instant parseTime(String raw, String field, boolean endOfDay, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.strip();
        try {
            if (v.length() == 10) {
                LocalDate d = LocalDate.parse(v);
                return (endOfDay ? d.plusDays(1) : d).atStartOfDay(ClockConfig.SEOUL).toInstant();
            }
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            errors.add(new FieldError(field, "YYYY-MM-DD 또는 ISO-8601 시각이어야 해요."));
            return null;
        }
    }

    private static String optionalOf(String raw, Set<String> allowed, String field, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!allowed.contains(raw.strip())) {
            errors.add(new FieldError(field, String.join(", ", allowed.stream().sorted().toList()) + " 중 하나여야 해요."));
            return null;
        }
        return raw.strip();
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }
}
