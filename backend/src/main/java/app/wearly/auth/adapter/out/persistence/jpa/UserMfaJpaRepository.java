package app.wearly.auth.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMfaJpaRepository extends JpaRepository<UserMfaEntity, Long> {

  /**
   * 마지막 사용 단계를 올린다. 이미 그 단계 이상이면 바꾸지 않는다 (같은 코드를 두 요청이 동시에 보내도 한쪽만 1).
   *
   * @return 바꾼 행 수 (0 또는 1)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE tb_user_mfa SET last_used_step = :step"
              + " WHERE user_id = :userId AND (last_used_step IS NULL OR last_used_step < :step)",
      nativeQuery = true)
  int useStep(@Param("userId") long userId, @Param("step") long step);
}
