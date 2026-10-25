package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminWithdrawalDto;
import app.wearly.admin.application.port.in.dto.AdminWithdrawalSummaryDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;

/** 탈퇴 기록 관리 (doc/27 4단계, 07 문서 4장 "탈퇴 기록"): 목록·요약 읽기, 탈퇴 취소, 바로 삭제 */
public interface AdminWithdrawalUseCase {

  /**
   * 탈퇴 기록, 최근 신청순. q: 남은 회원의 이메일·이름 부분 일치 또는 회원 ID. state: OPEN · OVERDUE · CANCELED · PURGED.
   * reason: 사유 코드.
   *
   * <p>오류: 400 VALIDATION_FAILED (state·reason NOT_ALLOWED, page·size)
   */
  PageDto<AdminWithdrawalDto> list(String q, String state, String reason, PagingDto paging);

  /** 최근 days일(기본 30, 1~365) 요약. 오류: 400 VALIDATION_FAILED (days OUT_OF_RANGE) */
  AdminWithdrawalSummaryDto summary(Integer days);

  /**
   * 탈퇴 예정 회원의 탈퇴를 취소한다 (기록의 canceled_by ADMIN, 변경 이력 USER_WITHDRAWAL_CANCEL). 회원은 다시 로그인해 그대로 쓴다.
   * 오류: 404 NOT_FOUND (탈퇴 예정이 아님)
   */
  void cancel(long adminId, long userId);

  /**
   * 탈퇴 예정 회원을 기한과 상관없이 지금 지운다 (purged_by ADMIN, 변경 이력 USER_PURGE). 되돌릴 수 없다. 오류: 404 NOT_FOUND (탈퇴
   * 예정이 아님)
   */
  void purge(long adminId, long userId);
}
