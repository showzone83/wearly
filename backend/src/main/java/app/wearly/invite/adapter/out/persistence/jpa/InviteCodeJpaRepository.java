package app.wearly.invite.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_user_invite_code 리포지토리 */
public interface InviteCodeJpaRepository extends JpaRepository<InviteCodeEntity, Long> {

  Optional<InviteCodeEntity> findByCode(String code);

  /** 코드 주인 ID. 주인이 탈퇴 예정(doc/27)이면 빈 값 */
  @Query(
      value =
          "SELECT c.user_id FROM tb_user_invite_code c JOIN tb_user u ON u.user_id = c.user_id"
              + " WHERE c.code = :code AND u.status <> 'WITHDRAWN'",
      nativeQuery = true)
  Optional<Long> findLiveUserIdByCode(@Param("code") String code);

  /** 회원에게 코드가 없고 같은 코드도 없을 때만 넣는다. 넣었으면 1, 아니면 0 (고유 제약 위반으로 트랜잭션을 깨지 않는다) */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO tb_user_invite_code (user_id, code, created_at)"
              + " VALUES (:userId, :code, :now) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("userId") long userId, @Param("code") String code, @Param("now") Instant now);
}
