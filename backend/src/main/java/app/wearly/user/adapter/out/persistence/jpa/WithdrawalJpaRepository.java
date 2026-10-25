package app.wearly.user.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_user_withdrawal 리포지토리 */
public interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, Long> {

  /** 회원의 열린 기록 (부분 고유 인덱스 uq_user_withdrawal_open으로 하나뿐) */
  @Query(
      "SELECT w FROM WithdrawalEntity w WHERE w.userId = :userId"
          + " AND w.canceledAt IS NULL AND w.purgedAt IS NULL")
  Optional<WithdrawalEntity> findOpen(@Param("userId") long userId);

  /** 기한이 지난 열린 기록의 회원 ID (기한이 이른 순) */
  @Query(
      "SELECT w.userId FROM WithdrawalEntity w WHERE w.canceledAt IS NULL AND w.purgedAt IS NULL"
          + " AND w.purgeAfter <= :now ORDER BY w.purgeAfter, w.id")
  List<Long> findDueUserIds(@Param("now") Instant now, Limit limit);

  /** 기한이 지난 열린 기록에 삭제를 남긴다. 바꾼 행 수 (다른 서버가 먼저 잡았으면 0) */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE WithdrawalEntity w SET w.purgedAt = :now, w.purgedBy = :by"
          + " WHERE w.userId = :userId AND w.canceledAt IS NULL AND w.purgedAt IS NULL"
          + " AND w.purgeAfter <= :dueBy")
  int claimDue(
      @Param("userId") long userId,
      @Param("dueBy") Instant dueBy,
      @Param("now") Instant now,
      @Param("by") String by);

  /** 열린 기록에 삭제를 남긴다 (기한 무시, 관리자 바로 삭제). 바꾼 행 수 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE WithdrawalEntity w SET w.purgedAt = :now, w.purgedBy = :by"
          + " WHERE w.userId = :userId AND w.canceledAt IS NULL AND w.purgedAt IS NULL")
  int claimAny(@Param("userId") long userId, @Param("now") Instant now, @Param("by") String by);
}
