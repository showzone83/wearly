package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminWithdrawalUseCase;
import app.wearly.admin.application.port.in.dto.AdminWithdrawalDto;
import app.wearly.admin.application.port.in.dto.AdminWithdrawalSummaryDto;
import app.wearly.admin.application.port.in.dto.AdminWithdrawalSummaryDto.ReasonCount;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminWithdrawalQueryPort;
import app.wearly.admin.application.port.out.AdminWithdrawalQueryPort.WithdrawalFilter;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.AdminWithdrawalRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalStateCountRowDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import app.wearly.user.application.port.in.dto.WithdrawalDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 기록 관리 (doc/27 4단계): 목록·요약은 MyBatis 읽기, 취소·바로 삭제는 user 컨텍스트의 입력 포트 + 변경 이력 */
@Service
public class AdminWithdrawalService implements AdminWithdrawalUseCase {

  /** 목록 state 값 */
  static final List<String> STATES = List.of("OPEN", "OVERDUE", "CANCELED", "PURGED");

  /** tb_user_withdrawal.reason 값 (user 컨텍스트 WithdrawalReason과 같다) */
  static final List<String> REASONS =
      List.of("NOT_USEFUL", "BAD_RECOMMEND", "HARD_TO_USE", "PRIVACY", "OTHER_APP", "OTHER");

  static final int DEFAULT_DAYS = 30;
  static final int MAX_DAYS = 365;

  private final AdminWithdrawalQueryPort queries;
  private final WithdrawalUseCase withdrawals;
  private final RecordAuditPort audit;
  private final Clock clock;

  public AdminWithdrawalService(
      AdminWithdrawalQueryPort queries,
      WithdrawalUseCase withdrawals,
      RecordAuditPort audit,
      Clock clock) {
    this.queries = queries;
    this.withdrawals = withdrawals;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AdminWithdrawalDto> list(String q, String state, String reason, PagingDto paging) {
    String st = SearchText.blankToNull(state);
    String rs = SearchText.blankToNull(reason);
    List<FieldError> errors = new ArrayList<>();
    if (st != null && !STATES.contains(st)) {
      errors.add(FieldError.notAllowed("state", STATES));
    }
    if (rs != null && !REASONS.contains(rs)) {
      errors.add(FieldError.notAllowed("reason", REASONS));
    }
    BusinessException.throwIfAny(errors);
    String query = SearchText.blankToNull(q);
    WithdrawalFilter filter = new WithdrawalFilter(query, userIdOf(query), st, rs);
    Instant now = clock.instant();
    List<AdminWithdrawalDto> items =
        queries.findWithdrawals(filter, now, paging.size(), paging.offset()).stream()
            .map(r -> toDto(r, now))
            .toList();
    return new PageDto<>(
        items, paging.page(), paging.size(), queries.countWithdrawals(filter, now));
  }

  @Override
  @Transactional(readOnly = true)
  public AdminWithdrawalSummaryDto summary(Integer days) {
    int d = days == null ? DEFAULT_DAYS : days;
    if (d < 1 || d > MAX_DAYS) {
      throw BusinessException.validation(FieldError.outOfRange("days", 1, MAX_DAYS));
    }
    Instant now = clock.instant();
    Instant from = now.minus(Duration.ofDays(d)).truncatedTo(ChronoUnit.SECONDS);
    WithdrawalStateCountRowDto states = queries.countStates(from);
    List<ReasonCount> reasons =
        queries.countReasons(from).stream()
            .map(r -> new ReasonCount(r.reason(), r.count()))
            .toList();
    return new AdminWithdrawalSummaryDto(
        d,
        from,
        states.total(),
        states.open(),
        states.canceled(),
        states.purged(),
        queries.countOverdue(now),
        reasons);
  }

  @Override
  @Transactional
  public void cancel(long adminId, long userId) {
    WithdrawalDto open = withdrawals.findOpen(userId).orElseThrow(BusinessException::notFound);
    if (!withdrawals.cancel(userId, true)) {
      throw BusinessException.notFound();
    }
    audit.record(
        adminId,
        "USER_WITHDRAWAL_CANCEL",
        AuditTargets.USER,
        Long.toString(userId),
        before(open),
        Map.of("status", UserAccountDto.ACTIVE));
  }

  @Override
  @Transactional
  public void purge(long adminId, long userId) {
    WithdrawalDto open = withdrawals.findOpen(userId).orElseThrow(BusinessException::notFound);
    if (!withdrawals.purgeNow(userId)) {
      throw BusinessException.notFound();
    }
    audit.record(
        adminId,
        "USER_PURGE",
        AuditTargets.USER,
        Long.toString(userId),
        before(open),
        Map.of("purged", true));
  }

  private static Map<String, Object> before(WithdrawalDto w) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", UserAccountDto.WITHDRAWN);
    m.put("reason", w.reason());
    m.put("purgeAt", w.purgeAfter().truncatedTo(ChronoUnit.MILLIS).toString());
    return m;
  }

  /** 검색어가 숫자면 회원 ID로도 찾는다 */
  private static Long userIdOf(String q) {
    if (q == null || !q.chars().allMatch(Character::isDigit) || q.length() > 18) {
      return null;
    }
    return Long.parseLong(q);
  }

  private static AdminWithdrawalDto toDto(AdminWithdrawalRowDto r, Instant now) {
    String state = r.purgedAt() != null ? "PURGED" : r.canceledAt() != null ? "CANCELED" : "OPEN";
    boolean overdue = "OPEN".equals(state) && !r.purgeAfter().isAfter(now);
    return new AdminWithdrawalDto(
        r.id(),
        r.userId(),
        r.email(),
        r.name(),
        r.reason(),
        r.reasonText(),
        state,
        overdue,
        r.requestedAt(),
        r.purgeAfter(),
        r.canceledAt(),
        r.canceledBy(),
        r.purgedAt(),
        r.purgedBy());
  }
}
