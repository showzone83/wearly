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
import app.wearly.auth.application.port.in.MfaUseCase;
import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 목록(MyBatis)·정지·역할 지정·해제·2단계 인증 초기화(user·auth 컨텍스트 유스케이스) (07 문서 4장 "회원", 22 문서 4.1) */
@Service
public class AdminUserService implements AdminUserUseCase {

  static final List<String> STATUSES = List.of(UserAccountDto.ACTIVE, UserAccountDto.SUSPENDED);
  static final List<String> ROLES = List.of(UserAccountDto.ROLE_USER, UserAccountDto.ROLE_ADMIN);

  private final AdminQueryPort queries;
  private final UserUseCase users;
  private final SessionUseCase sessions;
  private final MfaUseCase mfa;
  private final RecordAuditPort audit;
  private final Clock clock;

  public AdminUserService(
      AdminQueryPort queries,
      UserUseCase users,
      SessionUseCase sessions,
      MfaUseCase mfa,
      RecordAuditPort audit,
      Clock clock) {
    this.queries = queries;
    this.users = users;
    this.sessions = sessions;
    this.mfa = mfa;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AdminUserDto> list(String q, String status, String role, PagingDto paging) {
    String st = SearchText.blankToNull(status);
    if (st != null && !STATUSES.contains(st)) {
      throw BusinessException.validation(FieldError.notAllowed("status", STATUSES));
    }
    String rl = SearchText.blankToNull(role);
    if (rl != null && !ROLES.contains(rl)) {
      throw BusinessException.validation(FieldError.notAllowed("role", ROLES));
    }
    UserFilter filter = new UserFilter(null, SearchText.blankToNull(q), st, rl);
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
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    List<FieldError> errors = new ArrayList<>();
    String status = user.status();
    if (patch.status().present()) {
      String v = patch.status().value();
      if (v == null || !STATUSES.contains(v)) {
        errors.add(FieldError.notAllowed("status", STATUSES));
      } else if (userId == adminId && UserAccountDto.SUSPENDED.equals(v)) {
        errors.add(new FieldError("status", FieldErrorCode.SELF_NOT_ALLOWED));
      } else {
        status = v;
      }
    }
    String role = user.role();
    if (patch.role().present()) {
      String v = patch.role().value();
      if (v == null || !ROLES.contains(v)) {
        errors.add(FieldError.notAllowed("role", ROLES));
      } else if (userId == adminId && !UserAccountDto.ROLE_ADMIN.equals(v)) {
        errors.add(new FieldError("role", FieldErrorCode.SELF_NOT_ALLOWED));
      } else {
        role = v;
      }
    }
    boolean promoting = !user.isAdmin() && UserAccountDto.ROLE_ADMIN.equals(role);
    if (promoting && UserAccountDto.SUSPENDED.equals(status)) {
      // 정지된 회원은 관리자로 지정할 수 없다 (22 문서)
      errors.add(new FieldError("role", FieldErrorCode.CONFLICT));
    }
    BusinessException.throwIfAny(errors);
    boolean wasUsableAdmin = user.isAdmin() && user.isActive();
    boolean staysUsableAdmin =
        UserAccountDto.ROLE_ADMIN.equals(role) && UserAccountDto.ACTIVE.equals(status);
    if (wasUsableAdmin && !staysUsableAdmin) {
      // 쓸 수 있는 관리자가 한 명도 남지 않으면 409 (동시 해제에도 맞게 관리자 행을 잠그고 센다)
      List<Long> admins = users.lockActiveAdmins();
      if (admins.stream().noneMatch(id -> id != userId)) {
        throw new BusinessException(ErrorCode.LAST_ADMIN);
      }
    }
    Map<String, Object> before = state(user);
    boolean suspending =
        !UserAccountDto.SUSPENDED.equals(user.status()) && UserAccountDto.SUSPENDED.equals(status);
    boolean demoting = user.isAdmin() && !UserAccountDto.ROLE_ADMIN.equals(role);
    UserAccountDto after = users.changeAccess(userId, status, role);
    if (suspending || demoting) {
      // 정지·관리자 해제는 그 회원의 세션을 모두 끊어 바로 반영한다 (FORCED_LOGOUT)
      sessions.endUserSessions(userId);
    }
    String action = user.role().equals(role) ? "USER_UPDATE" : "USER_ROLE_CHANGE";
    audit.record(adminId, action, AuditTargets.USER, Long.toString(userId), before, state(after));
    return view(userId);
  }

  @Override
  @Transactional
  public void resetMfa(long adminId, long userId) {
    if (userId == adminId) {
      throw BusinessException.validation("id", FieldErrorCode.SELF_NOT_ALLOWED);
    }
    UserAccountDto user = users.get(userId);
    final MfaStatusDto before = mfa.remove(userId);
    final int ended = sessions.endUserSessions(userId);
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("email", user.email());
    b.put("enrolled", before.enrolled());
    b.put("recoveryCodesLeft", before.recoveryCodesLeft());
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("enrolled", false);
    a.put("endedSessions", ended);
    audit.record(adminId, "USER_MFA_RESET", AuditTargets.USER, Long.toString(userId), b, a);
  }

  private AdminUserDto view(long userId) {
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
        r.retiredItemCount(),
        r.aiToday(),
        r.mfaEnrolled());
  }
}
