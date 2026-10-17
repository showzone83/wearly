package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.AdminViews.AiLogDetailView;
import app.wearly.admin.application.port.in.AdminViews.AiLogSearch;
import app.wearly.admin.application.port.in.AdminViews.AiLogView;
import app.wearly.admin.application.port.in.AdminViews.AiToday;
import app.wearly.admin.application.port.in.AdminViews.AuditView;
import app.wearly.admin.application.port.in.AdminViews.DashboardView;
import app.wearly.admin.application.port.in.AdminViews.ProviderStatView;
import app.wearly.admin.application.port.in.Page;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminQueryPort.AiLogFilter;
import app.wearly.admin.application.port.out.AdminRows.AiCounts;
import app.wearly.admin.application.port.out.AdminRows.AiLogRow;
import app.wearly.admin.application.port.out.AdminRows.AuditRow;
import app.wearly.admin.application.port.out.AdminRows.Counts;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.common.domain.Ids;
import app.wearly.common.domain.Times;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
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
public class AdminDashboardService implements AdminDashboardUseCase {

  static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");
  static final Set<String> FEATURES = Set.of("ITEM_ANALYZE", "ADMIN_TEST");

  private final AdminQueryPort queries;
  private final Clock clock;

  public AdminDashboardService(AdminQueryPort queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public DashboardView dashboard() {
    Instant now = clock.instant();
    Instant weekAgo = now.minus(Duration.ofDays(7));
    LocalDate today = Zones.today(clock);
    Counts c = queries.counts(weekAgo);
    AiCounts ai = queries.aiCounts(Zones.startOf(today), Zones.startOf(today.plusDays(1)));
    List<ProviderStatView> byProvider =
        queries.providerStats(weekAgo, now.plusSeconds(1)).stream()
            .map(
                r ->
                    new ProviderStatView(
                        r.providerId,
                        r.success,
                        r.failed,
                        r.avgLatencyMs,
                        r.inputTokens == null ? 0 : r.inputTokens,
                        r.outputTokens == null ? 0 : r.outputTokens))
            .toList();
    return new DashboardView(
        c.users, c.newUsers7d, c.items, new AiToday(ai.total, ai.success, ai.failed), byProvider);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AiLogView> aiLogs(AiLogSearch search, Page.Paging paging) {
    String st = SearchText.blankToNull(search.status());
    if (st != null && !STATUSES.contains(st)) {
      throw ApiException.validation("status", "SUCCESS 또는 FAILED여야 해요.");
    }
    String ft = SearchText.blankToNull(search.feature());
    if (ft != null && !FEATURES.contains(ft)) {
      throw ApiException.validation("feature", "ITEM_ANALYZE 또는 ADMIN_TEST여야 해요.");
    }
    String email = SearchText.blankToNull(search.userEmail());
    AiLogFilter filter =
        new AiLogFilter(
            SearchText.blankToNull(search.providerId()),
            st,
            email == null ? null : SearchText.containsPattern(email.strip()),
            ft,
            time("from", search.from(), false),
            time("to", search.to(), true));
    List<AiLogView> items =
        queries.findAiLogs(filter, paging.size(), paging.offset()).stream()
            .map(AdminDashboardService::toView)
            .toList();
    return new Page<>(items, paging.page(), paging.size(), queries.countAiLogs(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public AiLogDetailView aiLog(long id) {
    AiLogRow r = queries.findAiLog(id);
    if (r == null) {
      throw ApiException.notFound();
    }
    return new AiLogDetailView(
        Ids.format(r.id),
        Times.iso(r.createdAt),
        Ids.format(r.userId),
        r.userEmail,
        r.feature,
        r.providerId,
        r.model,
        r.status,
        r.latencyMs,
        r.inputTokens,
        r.outputTokens,
        r.imageBytes,
        r.imageSha256,
        r.errorCode,
        r.result);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditView> auditLogs(String targetType, Page.Paging paging) {
    String tt = SearchText.blankToNull(targetType);
    if (tt != null && !AuditTargets.ALL.contains(tt)) {
      throw ApiException.validation("targetType", "CODE, AI_PROVIDER, USER 중 하나여야 해요.");
    }
    List<AuditView> items =
        queries.findAuditLogs(tt, paging.size(), paging.offset()).stream()
            .map(AdminDashboardService::toView)
            .toList();
    return new Page<>(items, paging.page(), paging.size(), queries.countAuditLogs(tt));
  }

  private static AiLogView toView(AiLogRow r) {
    return new AiLogView(
        Ids.format(r.id),
        Times.iso(r.createdAt),
        Ids.format(r.userId),
        r.userEmail,
        r.feature,
        r.providerId,
        r.model,
        r.status,
        r.latencyMs,
        r.inputTokens,
        r.outputTokens,
        r.imageBytes,
        summary(r.result),
        r.errorCode);
  }

  private static AuditView toView(AuditRow r) {
    return new AuditView(
        Ids.format(r.id),
        Ids.format(r.adminUserId),
        r.adminEmail,
        r.action,
        r.targetType,
        r.targetId,
        r.before,
        r.after,
        Times.iso(r.createdAt));
  }

  /** 결과 요약: "네이비 울 니트 · TOP/KNIT · navy" */
  static String summary(Map<String, Object> result) {
    if (result == null) {
      return null;
    }
    return Objects.toString(result.get("name"), "")
        + " · "
        + result.get("category")
        + "/"
        + result.get("subType")
        + " · "
        + result.get("color");
  }

  private static Instant time(String field, String raw, boolean end) {
    String v = SearchText.blankToNull(raw);
    if (v == null) {
      return null;
    }
    try {
      if (v.length() == 10) {
        LocalDate d = LocalDate.parse(v);
        return Zones.startOf(end ? d.plusDays(1) : d);
      }
      return Instant.parse(v);
    } catch (DateTimeParseException e) {
      throw ApiException.validation(field, "YYYY-MM-DD 또는 ISO-8601 시각이어야 해요.");
    }
  }
}
