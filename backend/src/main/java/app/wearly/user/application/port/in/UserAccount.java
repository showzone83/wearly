package app.wearly.user.application.port.in;

import java.time.Instant;

/** 다른 컨텍스트(인증·관리자)와 웹 어댑터에 내보내는 회원 정보 */
public record UserAccount(long id, String email, String name, String role, String status, Instant createdAt) {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
