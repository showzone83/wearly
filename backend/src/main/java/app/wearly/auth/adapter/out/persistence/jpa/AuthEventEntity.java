package app.wearly.auth.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * tb_auth_event JPA 엔티티 (로그인 이력, 16 문서 3장). 넣기는 AuthEventJpaRepository.insertIfUserExists(네이티브
 * SQL)로 하고, 이 엔티티는 스키마 검증(ddl-auto: validate)과 단건 조회용이다. 목록은 관리자 MyBatis 조회가 읽는다.
 */
@Entity
@Table(name = "tb_auth_event")
public class AuthEventEntity {

  @Id
  @Column(name = "auth_event_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "type", nullable = false, length = 20)
  private String type;

  @Column(name = "session_id", length = 36)
  private String sessionId;

  @Column(name = "client", length = 10)
  private String client;

  @Column(name = "ip", length = 45)
  private String ip;

  @Column(name = "user_agent", length = 300)
  private String userAgent;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** SSO 로그인·거부의 공급자 ID (V22) */
  @Column(name = "sso_provider", length = 50)
  private String ssoProvider;

  /** SSO 로그인 때 IdP의 amr (V22) */
  @Column(name = "sso_amr", length = 100)
  private String ssoAmr;

  protected AuthEventEntity() {}

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getType() {
    return type;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getSsoProvider() {
    return ssoProvider;
  }

  public String getSsoAmr() {
    return ssoAmr;
  }
}
