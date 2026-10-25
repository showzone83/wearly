package app.wearly.user.adapter.out.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_user_withdrawal 리포지토리 */
public interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, Long> {

  /** 회원의 열린 기록 (부분 고유 인덱스 uq_user_withdrawal_open으로 하나뿐) */
  @Query(
      "SELECT w FROM WithdrawalEntity w WHERE w.userId = :userId"
          + " AND w.canceledAt IS NULL AND w.purgedAt IS NULL")
  Optional<WithdrawalEntity> findOpen(@Param("userId") long userId);
}
