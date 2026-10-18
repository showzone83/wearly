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
import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.auth.application.port.in.dto.SessionStatsDto;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.outfit.application.port.in.OutfitFeatures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드·AI 분석 기록·변경 이력 조회 (MyBatis, 읽기 전용) */
@Service
public class AdminDashboardService implements AdminDashboardUseCase {

  static final List<String> STATUSES = List.of("SUCCESS", "FAILED");

  /** 기록 필터에 쓸 수 있는 기능: ai 자체 기능 + AI를 쓰는 컨텍스트의 기능 (L6, 순서는 오류 응답 params.allowed 순서) */
  static final List<String> FEATURE_NAMES =
      Stream.concat(AiFeatures.ALL.stream(), Stream.of(OutfitFeatures.OUTFIT_RECOMMEND)).toList();

  static final Set<String> FEATURES = Set.copyOf(FEATURE_NAMES);

  private final AdminQueryPort queries;
  private final SessionUseCase sessions;
  private final Clock clock;

  public AdminDashboardService(AdminQueryPort queries, SessionUseCase sessions, Clock clock) {
    this.queries = queries;
    this.sessions = sessions;
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
    SessionStatsDto st = sessions.stats(now.minus(Duration.ofHours(24)));
    return new DashboardDto(
        c.users(),
        c.newUsers7d(),
        c.items(),
        new AiTodayDto(ai.total(), ai.success(), ai.failed()),
        byProvider,
        st.activeSessions(),
        st.activeUsers());
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AiLogDto> aiLogs(AiLogSearchDto search, PagingDto paging) {
    String st = SearchText.blankToNull(search.status());
    if (st != null && !STATUSES.contains(st)) {
      throw BusinessException.validation(FieldError.notAllowed("status", STATUSES));
    }
    String ft = SearchText.blankToNull(search.feature());
    if (ft != null && !FEATURES.contains(ft)) {
      throw BusinessException.validation(FieldError.notAllowed("feature", FEATURE_NAMES));
    }
    String email = SearchText.blankToNull(search.userEmail());
    AiLogFilter filter =
        new AiLogFilter(
            SearchText.blankToNull(search.providerId()),
            st,
            email,
            ft,
            SearchTimes.parse("from", search.from(), false),
            SearchTimes.parse("to", search.to(), true));
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
      throw BusinessException.notFound();
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
      throw BusinessException.validation(FieldError.notAllowed("targetType", AuditTargets.ALL));
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
        r.summary(),
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
}
