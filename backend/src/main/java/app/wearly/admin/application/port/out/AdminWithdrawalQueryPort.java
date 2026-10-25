package app.wearly.admin.application.port.out;

import app.wearly.admin.application.port.out.dto.AdminWithdrawalRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalReasonCountRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalStateCountRowDto;
import java.time.Instant;
import java.util.List;

/** 탈퇴 기록 읽기 (MyBatis, tb_user_withdrawal과 아직 남은 회원, doc/27 4단계) */
public interface AdminWithdrawalQueryPort {

  List<AdminWithdrawalRowDto> findWithdrawals(
      WithdrawalFilter filter, Instant now, int limit, long offset);

  long countWithdrawals(WithdrawalFilter filter, Instant now);

  /** from 이후 신청한 탈퇴의 상태별 수 */
  WithdrawalStateCountRowDto countStates(Instant from);

  /** from 이후 신청한 탈퇴의 사유별 수 (많은 순) */
  List<WithdrawalReasonCountRowDto> countReasons(Instant from);

  /** 기한이 지났는데 아직 지우지 않은 탈퇴 예정 수 (예약 삭제가 밀렸는지, 기간 무관) */
  long countOverdue(Instant now);

  /**
   * query: 남은 회원의 이메일·이름 부분 일치 또는 회원 ID. state: OPEN(탈퇴 예정) · OVERDUE(기한 지남) · CANCELED · PURGED
   * (null이면 모두). reason: 사유 코드 (null이면 모두)
   */
  record WithdrawalFilter(String query, Long userId, String state, String reason) {}
}
