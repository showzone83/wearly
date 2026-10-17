package app.wearly.auth.adapter.out.persistence.jpa;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthEventJpaRepository extends JpaRepository<AuthEventEntity, Long> {

  /**
   * 회원이 있을 때만 넣는다. 탈퇴한 회원의 회전된 Refresh 토큰이 만료 전에 다시 오면(REFRESH_REUSED) 외래 키 오류 대신 기록을 건너뛴다.
   *
   * @return 넣은 행 수 (0 또는 1)
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO tb_auth_event"
              + " (user_id, type, session_id, client, ip, user_agent, created_at)"
              + " SELECT :userId, :type, :sessionId, :client, :ip, :userAgent, :createdAt"
              + " WHERE EXISTS (SELECT 1 FROM tb_user WHERE user_id = :userId)",
      nativeQuery = true)
  int insertIfUserExists(
      @Param("userId") long userId,
      @Param("type") String type,
      @Param("sessionId") String sessionId,
      @Param("client") String client,
      @Param("ip") String ip,
      @Param("userAgent") String userAgent,
      @Param("createdAt") Instant createdAt);
}
