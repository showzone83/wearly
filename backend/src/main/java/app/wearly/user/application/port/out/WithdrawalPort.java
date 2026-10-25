package app.wearly.user.application.port.out;

import app.wearly.user.domain.Withdrawal;
import app.wearly.user.domain.Withdrawal.Closer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 탈퇴 기록 저장·조회 (tb_user_withdrawal, JPA) */
public interface WithdrawalPort {

  /** 새 기록이면 만들고(id 채움), 있으면 닫힌 시각·닫은 쪽을 바꾼다 */
  Withdrawal save(Withdrawal withdrawal);

  /** 회원의 열린(취소·삭제 안 된) 기록. 하나뿐이다 (부분 고유 인덱스) */
  Optional<Withdrawal> findOpen(long userId);

  /** 기한(purge_after)이 now 이전인 열린 기록의 회원 ID, 기한이 이른 순 최대 limit명 */
  List<Long> findDueUserIds(Instant now, int limit);

  /**
   * 회원 삭제 처리권을 잡는다: 열린 기록에 삭제 시각·지운 쪽을 남긴다(UPDATE). dueBy가 있으면 기한이 그 시각 이전일 때만. 다른 서버가 먼저 잡았거나 그새
   * 취소됐으면 false. 같은 트랜잭션에서 회원을 지우지 못하면 함께 되돌아간다
   *
   * @param dueBy 예약 작업은 지금 시각, 관리자 바로 삭제는 null(기한 무시)
   */
  boolean claimPurge(long userId, Instant dueBy, Instant now, Closer by);
}
