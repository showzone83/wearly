package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminUserUseCase;
import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminQueryPort.UserFilter;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 목록(MyBatis)·정지·역할 변경(user·auth 컨텍스트 유스케이스) (07 문서 4장 "회원") */
@Service
public class AdminUserService implements AdminUserUseCase {

  static final Set<String> STATUSES = Set.of(UserAccountDto.ACTIVE, UserAccountDto.SUSPENDED);
  static final Set<String> ROLES = Set.of(UserAccountDto.ROLE_USER, UserAccountDto.ROLE_ADMIN);

  private final AdminQueryPort queries;
  private final UserUseCase users;
  private final AccountUseCase accounts;
  private final RecordAuditPort audit;
  private final Clock clock;

  public AdminUserService(
      AdminQueryPort queries,
      UserUseCase users,
      AccountUseCase accounts,
      RecordAuditPort audit,
      Clock clock) {
    this.queries = queries;
    this.users = users;
    this.accounts = accounts;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AdminUserDto> list(String q, String status, String role, PagingDto paging) {
    String st = SearchText.blankToNull(status);
    if (st != null && !STATUSES.contains(st)) {
      throw ApiException.validation("status", "ACTIVE 또는 SUSPENDED여야 해요.");
    }
    String rl = SearchText.blankToNull(role);
    if (rl != null && !ROLES.contains(rl)) {
      throw ApiException.validation("role", "USER 또는 ADMIN이어야 해요.");
    }
    String query = SearchText.blankToNull(q);
    UserFilter filter =
        new UserFilter(null, query == null ? null : SearchText.containsPattern(query), st, rl);
    return page(filter, paging);
  }

  private PageDto<AdminUserDto> page(UserFilter filter, PagingDto paging) {
    LocalDate today = Zones.today(clock);
    List<AdminUserDto> items =
        queries
            .findUsers(
                filter,
                Zones.startOf(today),
                Zones.startOf(today.plusDays(1)),
                paging.size(),
                paging.offset())
            .stream()
            .map(AdminUserService::toView)
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countUsers(filter));
  }

  @Override
  @Transactional
  public AdminUserDto patch(long adminId, long userId, PatchUserDto patch) {
    UserAccountDto user = users.get(userId);
    if (patch == null) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    List<FieldError> errors = new ArrayList<>();
    String status = user.status();
    if (patch.status().present()) {
      String v = patch.status().value();
      if (v == null || !STATUSES.contains(v)) {
        errors.add(new FieldError("status", "ACTIVE 또는 SUSPENDED여야 해요."));
      } else if (userId == adminId && UserAccountDto.SUSPENDED.equals(v)) {
        errors.add(new FieldError("status", "자기 자신은 정지할 수 없어요."));
      } else {
        status = v;
      }
    }
    String role = user.role();
    if (patch.role().present()) {
      String v = patch.role().value();
      if (v == null || !ROLES.contains(v)) {
        errors.add(new FieldError("role", "USER 또는 ADMIN이어야 해요."));
      } else if (userId == adminId && !UserAccountDto.ROLE_ADMIN.equals(v)) {
        errors.add(new FieldError("role", "자기 자신의 관리자 역할은 내릴 수 없어요."));
      } else {
        role = v;
      }
    }
    ApiException.throwIfAny(errors);
    Map<String, Object> before = state(user);
    boolean suspending =
        !UserAccountDto.SUSPENDED.equals(user.status()) && UserAccountDto.SUSPENDED.equals(status);
    UserAccountDto after = users.changeAccess(userId, status, role);
    if (suspending) {
      accounts.revokeAllRefreshTokens(userId);
    }
    audit.record(
        adminId, "USER_UPDATE", AuditTargets.USER, Long.toString(userId), before, state(after));
    return page(new UserFilter(userId, null, null, null), new PagingDto(0, 1)).items().getFirst();
  }

  private static Map<String, Object> state(UserAccountDto u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("email", u.email());
    m.put("status", u.status());
    m.put("role", u.role());
    return m;
  }

  private static AdminUserDto toView(UserListRowDto r) {
    return new AdminUserDto(
        r.id(),
        r.email(),
        r.name(),
        r.role(),
        r.status(),
        r.createdAt(),
        r.itemCount(),
        r.aiToday());
  }
}
