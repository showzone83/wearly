package app.wearly.user.domain;

import java.time.Duration;

/**
 * 탈퇴 유예 설정 (wearly.user.withdrawal.*, doc/27). user/config가 서버 설정에서 만든다.
 *
 * @param grace 탈퇴 신청 뒤 회원을 지우기까지 기다리는 시간 (기본 30일)
 * @param purgeBatch 예약 삭제 한 번에 지우는 최대 회원 수 (기본 500)
 */
public record WithdrawalPolicy(Duration grace, int purgeBatch) {

  public WithdrawalPolicy {
    if (grace == null || grace.isNegative() || grace.isZero()) {
      throw new IllegalArgumentException("wearly.user.withdrawal.grace는 0보다 커야 해요: " + grace);
    }
  }
}
