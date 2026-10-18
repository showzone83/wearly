package app.wearly.auth.adapter.out.persistence.jpa;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeJpaRepository extends JpaRepository<MfaRecoveryCodeEntity, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "DELETE FROM tb_user_mfa_recovery_code WHERE user_id = :userId",
      nativeQuery = true)
  int deleteByUser(@Param("userId") long userId);

  /**
   * 쓰지 않은 그 코드를 쓴 것으로 표시한다 (동시에 같은 코드를 보내도 한쪽만 1).
   *
   * @return 바꾼 행 수 (0 또는 1)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE tb_user_mfa_recovery_code SET used_at = :usedAt"
              + " WHERE user_id = :userId AND code_hash = :hash AND used_at IS NULL",
      nativeQuery = true)
  int use(
      @Param("userId") long userId, @Param("hash") String hash, @Param("usedAt") Instant usedAt);

  @Query(
      value =
          "SELECT count(*) FROM tb_user_mfa_recovery_code"
              + " WHERE user_id = :userId AND used_at IS NULL",
      nativeQuery = true)
  int countUnused(@Param("userId") long userId);
}
