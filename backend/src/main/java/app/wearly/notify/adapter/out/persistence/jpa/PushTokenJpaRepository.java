package app.wearly.notify.adapter.out.persistence.jpa;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_push_token 리포지토리 (쓰기·단건 조회) */
public interface PushTokenJpaRepository extends JpaRepository<PushTokenEntity, Long> {

  Optional<PushTokenEntity> findByUserIdAndDeviceId(Long userId, String deviceId);

  /** 같은 토큰을 가진 다른 회원·기기의 행을 지운다 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "DELETE FROM PushTokenEntity t WHERE t.token = :token"
          + " AND NOT (t.userId = :userId AND t.deviceId = :deviceId)")
  int deleteOtherOwners(
      @Param("token") String token,
      @Param("userId") Long userId,
      @Param("deviceId") String deviceId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM PushTokenEntity t WHERE t.userId = :userId AND t.deviceId = :deviceId")
  int deleteByOwner(@Param("userId") Long userId, @Param("deviceId") String deviceId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM PushTokenEntity t WHERE t.token IN :tokens")
  int deleteByTokens(@Param("tokens") Collection<String> tokens);
}
