package app.wearly.user.domain;

import java.time.Instant;

/** 회원 (순수 도메인 모델). 저장은 영속성 어댑터가 UserEntity로 바꿔 한다 */
public class User {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private final Long id;
    private final String email;
    private String name;
    private String status;
    private String role;
    private final Instant createdAt;

    public User(Long id, String email, String name, String status, String role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** 아직 저장하지 않은 새 회원 (상태 ACTIVE) */
    public static User newUser(String email, String name, String role) {
        return new User(null, email, name, ACTIVE, role, null);
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public void changeStatus(String status) {
        this.status = status;
    }

    public void changeRole(String role) {
        this.role = role;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }

    public String role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
