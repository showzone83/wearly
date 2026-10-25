package app.wearly.auth.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LocalCredentialJpaRepository extends JpaRepository<LocalCredentialEntity, Long> {

  /** 기준 시각이 dueBy 이전이고 기준 시각 뒤로 알림을 보낸 적이 없다 (greatest는 NULL을 건너뛴다) */
  String DUE =
      "greatest(c.password_changed_at, c.password_postponed_at) <= :dueBy"
          + " AND (c.password_reminded_at IS NULL"
          + " OR c.password_reminded_at"
          + " < greatest(c.password_changed_at, c.password_postponed_at))";

  Optional<LocalCredentialEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<LocalCredentialEntity> findByUserId(Long userId);

  /** 변경 알림 대상 (28 문서 2단계). 기준 시각 = 정한 시각과 연장 시각 중 늦은 쪽. 회원 상태는 tb_user를 본다(활성만) */
  @Query(
      value =
          "SELECT c.user_id FROM tb_user_local_credential c"
              + " JOIN tb_user u ON u.user_id = c.user_id AND u.status = 'ACTIVE'"
              + " WHERE "
              + DUE
              + " ORDER BY greatest(c.password_changed_at, c.password_postponed_at), c.user_id"
              + " LIMIT :limit",
      nativeQuery = true)
  List<Long> findReminderDueUserIds(@Param("dueBy") Instant dueBy, @Param("limit") int limit);

  /** 알림 처리권 잡기. 바꾼 행 수 (그새 바꿨거나 연장했거나 다른 서버가 먼저 잡았으면 0) */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE tb_user_local_credential c SET password_reminded_at = :now"
              + " WHERE c.user_id = :userId AND "
              + DUE,
      nativeQuery = true)
  int claimReminder(
      @Param("userId") long userId, @Param("dueBy") Instant dueBy, @Param("now") Instant now);
}
