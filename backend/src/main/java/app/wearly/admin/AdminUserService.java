package app.wearly.admin;

import app.wearly.admin.AdminDtos.PageResponse;
import app.wearly.admin.AdminDtos.UserItem;
import app.wearly.admin.AdminRows.UserRow;
import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import app.wearly.config.ClockConfig;
import app.wearly.user.User;
import app.wearly.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 회원 관리: 목록(MyBatis)과 정지·해제·역할 변경(JPA).
 * 정지하면 로그인 403 ACCOUNT_SUSPENDED, 남은 Access 토큰 401 TOKEN_INVALID, refresh 401 REFRESH_INVALID가 된다.
 */
@Service
public class AdminUserService {

    static final Set<String> STATUSES = Set.of(User.ACTIVE, User.SUSPENDED);
    static final Set<String> ROLES = Set.of(User.ROLE_USER, User.ROLE_ADMIN);

    private final AdminQueryMapper queries;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(AdminQueryMapper queries, UserRepository users, AuditService audit, Clock clock) {
        this.queries = queries;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserItem> list(String q, String status, String role, Paging paging) {
        List<FieldError> errors = new ArrayList<>();
        String st = optionalOf(status, STATUSES, "status", errors);
        String ro = optionalOf(role, ROLES, "role", errors);
        throwIfAny(errors);
        String pattern = q == null || q.isBlank() ? null : "%" + escapeLike(q.strip()) + "%";
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        List<UserItem> items = queries.findUsers(pattern, st, ro, from, to, paging.offset(), paging.size()).stream()
                .map(AdminUserService::toItem)
                .toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countUsersFiltered(pattern, st, ro));
    }

    /** { status?, role? }. 자기 자신의 역할은 내릴 수 없고 자기 자신을 정지할 수도 없다 (400) */
    @Transactional
    public UserItem patch(long adminId, long userId, JsonNode body) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        String status = body.has("status") ? requiredOf(body.get("status"), STATUSES, "status", errors) : null;
        String role = body.has("role") ? requiredOf(body.get("role"), ROLES, "role", errors) : null;
        if (userId == adminId && User.ROLE_USER.equals(role)) {
            errors.add(new FieldError("role", "자기 자신의 관리자 권한은 내릴 수 없어요."));
        }
        if (userId == adminId && User.SUSPENDED.equals(status)) {
            errors.add(new FieldError("status", "자기 자신은 정지할 수 없어요."));
        }
        throwIfAny(errors);
        Map<String, Object> before = AdminBootstrap.state(user);
        if (status != null) {
            user.changeStatus(status);
        }
        if (role != null) {
            user.changeRole(role);
        }
        users.saveAndFlush(user);
        Map<String, Object> after = AdminBootstrap.state(user);
        if (!after.equals(before)) {
            audit.record(adminId, "USER_UPDATE", AuditService.USER, String.valueOf(userId), before, after);
        }
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        return toItem(queries.findUser(userId, from, to));
    }

    private static UserItem toItem(UserRow r) {
        return new UserItem(Ids.format(r.id), r.email, r.name, r.role, r.status, Times.iso(r.createdAt), r.itemCount,
                r.aiToday);
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

    private static String requiredOf(JsonNode n, Set<String> allowed, String field, List<FieldError> errors) {
        if (n == null || !n.isString() || !allowed.contains(n.asString())) {
            errors.add(new FieldError(field, String.join(", ", allowed.stream().sorted().toList()) + " 중 하나여야 해요."));
            return null;
        }
        return n.asString();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }
}
