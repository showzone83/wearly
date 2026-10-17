package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.dto.AiLogDetailDto;
import app.wearly.admin.application.port.in.dto.AiLogDto;
import app.wearly.admin.application.port.in.dto.AiLogSearchDto;
import app.wearly.admin.application.port.in.dto.AuditLogDto;
import app.wearly.admin.application.port.in.dto.DashboardDto;
import app.wearly.admin.application.port.in.dto.DashboardDto.AiTodayDto;
import app.wearly.admin.application.port.in.dto.DashboardDto.ProviderUsageDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminQueryPort.AiLogFilter;
import app.wearly.admin.application.port.out.dto.AiCountsDto;
import app.wearly.admin.application.port.out.dto.AiLogRowDto;
import app.wearly.admin.application.port.out.dto.AuditLogRowDto;
import app.wearly.admin.application.port.out.dto.DashboardCountsDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.ai.application.port.in.AiFeatures;
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
  static final Set<String> FEATURES = Set.copyOf(AiFeatures.ALL);

  private final AdminQueryPort queries;
  private final Clock clock;

  public AdminDashboardService(AdminQueryPort queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public DashboardDto dashboard() {
    Instant now = clock.instant();
    Instant weekAgo = now.minus(Duration.ofDays(7));
    LocalDate today = Zones.today(clock);
    DashboardCountsDto c = queries.counts(weekAgo);
    AiCountsDto ai = queries.aiCounts(Zones.startOf(today), Zones.startOf(today.plusDays(1)));
    List<ProviderUsageDto> byProvider =
        queries.providerStats(weekAgo, now.plusSeconds(1)).stream()
            .map(
                r ->
                    new ProviderUsageDto(
                        r.providerId(),
                        r.success(),
                        r.failed(),
                        r.avgLatencyMs(),
                        r.inputTokens(),
                        r.outputTokens()))
            .toList();
    return new DashboardDto(
        c.users(),
        c.newUsers7d(),
        c.items(),
        new AiTodayDto(ai.total(), ai.success(), ai.failed()),
        byProvider);
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AiLogDto> aiLogs(AiLogSearchDto search, PagingDto paging) {
    String st = SearchText.blankToNull(search.status());
    if (st != null && !STATUSES.contains(st)) {
      throw ApiException.validation("status", "SUCCESS 또는 FAILED여야 해요.");
    }
    String ft = SearchText.blankToNull(search.feature());
    if (ft != null && !FEATURES.contains(ft)) {
      throw ApiException.validation("feature", String.join(", ", AiFeatures.ALL) + " 중 하나여야 해요.");
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
    List<AiLogDto> items =
        queries.findAiLogs(filter, paging.size(), paging.offset()).stream()
            .map(AdminDashboardService::toView)
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countAiLogs(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public AiLogDetailDto aiLog(long id) {
    AiLogRowDto r = queries.findAiLog(id);
    if (r == null) {
      throw ApiException.notFound();
    }
    return new AiLogDetailDto(
        r.id(),
        r.createdAt(),
        r.userId(),
        r.userEmail(),
        r.feature(),
        r.providerId(),
        r.model(),
        r.status(),
        r.latencyMs(),
        r.inputTokens(),
        r.outputTokens(),
        r.imageBytes(),
        r.imageSha256(),
        r.errorCode(),
        r.result());
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AuditLogDto> auditLogs(String targetType, PagingDto paging) {
    String tt = SearchText.blankToNull(targetType);
    if (tt != null && !AuditTargets.ALL.contains(tt)) {
      throw ApiException.validation("targetType", "CODE, AI_PROVIDER, USER 중 하나여야 해요.");
    }
    List<AuditLogDto> items =
        queries.findAuditLogs(tt, paging.size(), paging.offset()).stream()
            .map(AdminDashboardService::toView)
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countAuditLogs(tt));
  }

  private static AiLogDto toView(AiLogRowDto r) {
    return new AiLogDto(
        r.id(),
        r.createdAt(),
        r.userId(),
        r.userEmail(),
        r.feature(),
        r.providerId(),
        r.model(),
        r.status(),
        r.latencyMs(),
        r.inputTokens(),
        r.outputTokens(),
        r.imageBytes(),
        summary(r.result()),
        r.errorCode());
  }

  private static AuditLogDto toView(AuditLogRowDto r) {
    return new AuditLogDto(
        r.id(),
        r.adminUserId(),
        r.adminEmail(),
        r.action(),
        r.targetType(),
        r.targetId(),
        r.before(),
        r.after(),
        r.createdAt());
  }

  /** 결과 요약: 사진 분석 "네이비 울 니트 · TOP/KNIT · navy", 코디 추천 "코디 후보 3개" */
  static String summary(Map<String, Object> result) {
    if (result == null) {
      return null;
    }
    if (result.get("candidates") instanceof List<?> candidates) {
      return "코디 후보 " + candidates.size() + "개";
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
