package app.wearly.auth.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

  /** 동시에 같은 토큰으로 갱신하면 한 요청만 회전하도록 행을 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshTokenEntity t where t.tokenHash = :hash")
  Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("hash") String hash);

  Optional<RefreshTokenEntity> findByTokenHash(String hash);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update RefreshTokenEntity t set t.revokedAt = :at where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("at") Instant at);

  /** 회원 정지: 그 회원의 살아 있는 Refresh 토큰을 모두 폐기한다 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update RefreshTokenEntity t set t.revokedAt = :at where t.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") Long userId, @Param("at") Instant at);
}
