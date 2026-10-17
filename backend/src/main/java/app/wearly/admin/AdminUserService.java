package app.wearly.admin;

import app.wearly.admin.AdminQueryMapper.UserFilter;
import app.wearly.admin.AdminRows.UserRow;
import app.wearly.auth.RefreshTokenRepository;
import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import app.wearly.config.ClockConfig;
import app.wearly.user.User;
import app.wearly.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 회원 목록(MyBatis)·정지·역할 변경(JPA) (07 문서 4장 "회원") */
@Service
public class AdminUserService {

    static final Set<String> STATUSES = Set.of(User.ACTIVE, User.SUSPENDED);
    static final Set<String> ROLES = Set.of(User.ROLE_USER, User.ROLE_ADMIN);

    private final AdminQueryMapper queries;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(AdminQueryMapper queries, UserRepository users, RefreshTokenRepository refreshTokens,
            AuditService audit, Clock clock) {
        this.queries = queries;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    /** aiToday = 오늘(한국 시간) 성공한 사진 분석 수 (한도에 세는 것과 같음) */
    public record AdminUserDto(String id, String email, String name, String role, String status, String createdAt,
            long itemCount, long aiToday) {
    }

    /** q: 이메일·이름 부분 일치 (대소문자 무시) */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserDto> list(String q, String status, String role, PageResponse.Paging paging) {
        String st = AdminLogService.blankToNull(status);
        if (st != null && !STATUSES.contains(st)) {
            throw ApiException.validation("status", "ACTIVE 또는 SUSPENDED여야 해요.");
        }
        String rl = AdminLogService.blankToNull(role);
        if (rl != null && !ROLES.contains(rl)) {
            throw ApiException.validation("role", "USER 또는 ADMIN이어야 해요.");
        }
        String query = AdminLogService.blankToNull(q);
        UserFilter filter = new UserFilter(null, query == null ? null : "%" + AdminLogService.escapeLike(query) + "%", st, rl);
        return page(filter, paging);
    }

    private PageResponse<AdminUserDto> page(UserFilter filter, PageResponse.Paging paging) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        List<AdminUserDto> items = queries.findUsers(filter, today.atStartOfDay(ClockConfig.SEOUL).toInstant(),
                        today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant(), paging.size(), paging.offset())
                .stream().map(AdminUserService::toDto).toList();
        return new PageResponse<>(items, paging.page(), paging.size(), queries.countUsers(filter));
    }

    /**
     * { status?, role? }. 자기 자신의 역할을 내리거나 자기 자신을 정지할 수는 없다 (400).
     * 정지하면 그 회원의 Refresh 토큰을 모두 폐기한다 (남은 Access 토큰은 매 요청 회원 상태 확인으로 401).
     */
    @Transactional
    public AdminUserDto patch(long adminId, String id, JsonNode body) {
        long userId = Ids.parse(id);
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        String status = user.getStatus();
        if (body.has("status")) {
            String v = body.get("status").isString() ? body.get("status").asString() : null;
            if (v == null || !STATUSES.contains(v)) {
                errors.add(new FieldError("status", "ACTIVE 또는 SUSPENDED여야 해요."));
            } else if (userId == adminId && User.SUSPENDED.equals(v)) {
                errors.add(new FieldError("status", "자기 자신은 정지할 수 없어요."));
            } else {
                status = v;
            }
        }
        String role = user.getRole();
        if (body.has("role")) {
            String v = body.get("role").isString() ? body.get("role").asString() : null;
            if (v == null || !ROLES.contains(v)) {
                errors.add(new FieldError("role", "USER 또는 ADMIN이어야 해요."));
            } else if (userId == adminId && !User.ROLE_ADMIN.equals(v)) {
                errors.add(new FieldError("role", "자기 자신의 관리자 역할은 내릴 수 없어요."));
            } else {
                role = v;
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
        Map<String, Object> before = state(user);
        boolean suspending = !User.SUSPENDED.equals(user.getStatus()) && User.SUSPENDED.equals(status);
        user.changeStatus(status);
        user.changeRole(role);
        users.saveAndFlush(user);
        if (suspending) {
            refreshTokens.revokeAllForUser(userId, clock.instant());
        }
        audit.record(adminId, "USER_UPDATE", AuditService.USER, Long.toString(userId), before, state(user));
        return page(new UserFilter(userId, null, null, null), new PageResponse.Paging(0, 1)).items().getFirst();
    }

    private static Map<String, Object> state(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail());
        m.put("status", u.getStatus());
        m.put("role", u.getRole());
        return m;
    }

    private static AdminUserDto toDto(UserRow r) {
        return new AdminUserDto(Ids.format(r.id), r.email, r.name, r.role, r.status, Times.iso(r.createdAt), r.itemCount,
                r.aiToday);
    }
}
