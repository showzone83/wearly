package app.wearly.user.application.port.in;

import app.wearly.user.application.port.in.dto.WithdrawDto;
import app.wearly.user.application.port.in.dto.WithdrawalDto;
import java.time.Instant;
import java.util.Optional;

/**
 * 탈퇴 유예 (doc/27). 탈퇴를 신청하면 회원은 탈퇴 예정(WITHDRAWN)이 되고, 유예 기간 안에 다시 로그인하면 취소할 수 있다. 인증(로그인할 때 취소)도 이
 * 포트로 부른다.
 */
public interface WithdrawalUseCase {

  /**
   * 탈퇴 신청: 상태를 탈퇴 예정으로 바꾸고 기록(사유·시각)을 남긴 뒤 {@link UserWithdrawnListener}를 부른다(로그인 세션 끊기·푸시 토큰
   * 지우기·옷장 도우미 연결 끝내기). 옷·사진은 유예 기간 동안 남는다. 이미 탈퇴 예정이면 그 기록을 그대로 돌려준다.
   *
   * <p>오류: 400 VALIDATION_FAILED (reason REQUIRED · NOT_ALLOWED, reasonText TOO_LONG 200) · 404
   * NOT_FOUND (회원 없음)
   */
  WithdrawalDto withdraw(long userId, WithdrawDto input);

  /** 회원의 열린 탈퇴 (탈퇴 예정이 아니면 빈 값) */
  Optional<WithdrawalDto> findOpen(long userId);

  /**
   * 탈퇴 취소: 상태를 사용 중으로 되돌리고 기록에 취소를 남긴다. 끝난 옷장 도우미 연결·지운 푸시 토큰은 되살리지 않는다(앱이 토큰을 다시 등록한다). 탈퇴 예정이 아니면
   * 아무것도 하지 않고 false.
   *
   * @param byAdmin true면 관리자가 취소했다 (기록의 canceled_by ADMIN)
   */
  boolean cancel(long userId, boolean byAdmin);

  /**
   * 기한이 지난 탈퇴 예정 회원을 지운다 (예약 삭제, doc/27 2단계). 기록에 삭제(SCHEDULER)를 남기고 {@link UserUseCase#delete}로 회원
   * 행과 하위 데이터(CASCADE)·세션·사진을 지운다. 한 트랜잭션이라 지우지 못하면 기록도 되돌아가 다음에 다시 한다. 기한 전이거나 취소됐거나 다른 서버가 먼저
   * 지웠으면 false
   */
  boolean purgeIfDue(long userId, Instant now);

  /** 탈퇴 예정 회원을 기한과 상관없이 지금 지운다 (관리자 바로 삭제, 기록의 purged_by ADMIN). 탈퇴 예정이 아니면 false */
  boolean purgeNow(long userId);
}
