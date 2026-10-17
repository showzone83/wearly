package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminUserUseCase;
import app.wearly.admin.application.port.in.AdminViews.AdminUserView;
import app.wearly.admin.application.port.in.Page;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminQueryPort.UserFilter;
import app.wearly.admin.application.port.out.AdminRows.UserRow;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.Ids;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.domain.Times;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import app.wearly.user.application.port.in.UserAccount;
import app.wearly.user.application.port.in.UserUseCase;
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

  static final Set<String> STATUSES = Set.of(UserAccount.ACTIVE, UserAccount.SUSPENDED);
  static final Set<String> ROLES = Set.of(UserAccount.ROLE_USER, UserAccount.ROLE_ADMIN);

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
  public Page<AdminUserView> list(String q, String status, String role, Page.Paging paging) {
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

  private Page<AdminUserView> page(UserFilter filter, Page.Paging paging) {
    LocalDate today = Zones.today(clock);
    List<AdminUserView> items =
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
    return new Page<>(items, paging.page(), paging.size(), queries.countUsers(filter));
  }

  @Override
  @Transactional
  public AdminUserView patch(long adminId, long userId, PatchBody body) {
    UserAccount user = users.get(userId);
    if (body == null || !body.isObject()) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    List<FieldError> errors = new ArrayList<>();
    String status = user.status();
    if (body.has("status")) {
      String v = body.isString("status") ? body.string("status") : null;
      if (v == null || !STATUSES.contains(v)) {
        errors.add(new FieldError("status", "ACTIVE 또는 SUSPENDED여야 해요."));
      } else if (userId == adminId && UserAccount.SUSPENDED.equals(v)) {
        errors.add(new FieldError("status", "자기 자신은 정지할 수 없어요."));
      } else {
        status = v;
      }
    }
    String role = user.role();
    if (body.has("role")) {
      String v = body.isString("role") ? body.string("role") : null;
      if (v == null || !ROLES.contains(v)) {
        errors.add(new FieldError("role", "USER 또는 ADMIN이어야 해요."));
      } else if (userId == adminId && !UserAccount.ROLE_ADMIN.equals(v)) {
        errors.add(new FieldError("role", "자기 자신의 관리자 역할은 내릴 수 없어요."));
      } else {
        role = v;
      }
    }
    ApiException.throwIfAny(errors);
    Map<String, Object> before = state(user);
    boolean suspending =
        !UserAccount.SUSPENDED.equals(user.status()) && UserAccount.SUSPENDED.equals(status);
    UserAccount after = users.changeAccess(userId, status, role);
    if (suspending) {
      accounts.revokeAllRefreshTokens(userId);
    }
    audit.record(
        adminId, "USER_UPDATE", AuditTargets.USER, Long.toString(userId), before, state(after));
    return page(new UserFilter(userId, null, null, null), new Page.Paging(0, 1)).items().getFirst();
  }

  private static Map<String, Object> state(UserAccount u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("email", u.email());
    m.put("status", u.status());
    m.put("role", u.role());
    return m;
  }

  private static AdminUserView toView(UserRow r) {
    return new AdminUserView(
        Ids.format(r.id),
        r.email,
        r.name,
        r.role,
        r.status,
        Times.iso(r.createdAt),
        r.itemCount,
        r.aiToday);
  }
}
