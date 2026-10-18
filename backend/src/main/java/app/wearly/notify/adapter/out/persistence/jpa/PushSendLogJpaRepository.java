package app.wearly.notify.adapter.out.persistence.jpa;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** tb_push_send_log 리포지토리 */
public interface PushSendLogJpaRepository extends JpaRepository<PushSendLogEntity, Long> {

  /** 오늘 보낼 차례로 잡는다. 이미 있으면(오늘 보냄) 0, 새로 넣었으면 1. 그사이 예정이 지워졌으면 넣지 않는다(0) */
  @Modifying
  @Query(
      value =
          "INSERT INTO tb_push_send_log (planned_outfit_id, user_id, notify_kind, planned_date,"
              + " send_date) SELECT p.planned_outfit_id, :userId, :kind, :plannedDate, :sendDate"
              + " FROM tb_planned_outfit p WHERE p.planned_outfit_id = :planId"
              + " ON CONFLICT (planned_outfit_id, notify_kind, send_date) DO NOTHING",
      nativeQuery = true)
  int claim(
      @Param("planId") long planId,
      @Param("userId") long userId,
      @Param("kind") String kind,
      @Param("plannedDate") LocalDate plannedDate,
      @Param("sendDate") LocalDate sendDate);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "DELETE FROM PushSendLogEntity l WHERE l.plannedOutfitId = :planId"
          + " AND l.notifyKind = :kind AND l.sendDate = :sendDate")
  int release(
      @Param("planId") long planId,
      @Param("kind") String kind,
      @Param("sendDate") LocalDate sendDate);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM PushSendLogEntity l WHERE l.createdAt < :before")
  int deleteBefore(@Param("before") Instant before);
}
