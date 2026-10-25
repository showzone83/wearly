package app.wearly.user.application.port.in;

import app.wearly.user.application.port.in.dto.WithdrawalPurgeRunDto;
import java.time.Instant;

/** 탈퇴 예약 삭제 (doc/27 2단계). 예약 작업이 부르고, 테스트는 시각을 넣어 직접 부른다 */
public interface WithdrawalPurgeUseCase {

  /** 지금 기한이 지난 탈퇴 예정 회원을 지운다 */
  WithdrawalPurgeRunDto purgeDue();

  /**
   * now 기준으로 기한이 지난 탈퇴 예정 회원을 기한이 이른 순으로 한 번에 정해진 수(wearly.user.withdrawal.purge-batch)까지 지운다. 회원마다
   * 트랜잭션을 따로 두어 한 명이 실패해도 나머지는 계속한다
   */
  WithdrawalPurgeRunDto purgeDue(Instant now);
}
