package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.response.AdminWithdrawalResponse;
import app.wearly.admin.adapter.in.web.response.AdminWithdrawalSummaryResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.application.port.in.AdminWithdrawalUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 탈퇴 기록 API (doc/27 4단계, 07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
public class AdminWithdrawalController {

  private final AdminWithdrawalUseCase withdrawals;

  public AdminWithdrawalController(AdminWithdrawalUseCase withdrawals) {
    this.withdrawals = withdrawals;
  }

  /** 탈퇴 기록 { items, page, size, total } 최근 신청순. state: OPEN · OVERDUE · CANCELED · PURGED */
  @GetMapping
  public PageResponse<AdminWithdrawalResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String reason,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        withdrawals.list(q, state, reason, PagingDto.of(page, size)),
        AdminWithdrawalResponse::from);
  }

  /** 최근 days일(기본 30) 요약: 상태별·사유별 수, 기한이 지났는데 남은 탈퇴 예정 수 */
  @GetMapping("/summary")
  public AdminWithdrawalSummaryResponse summary(@RequestParam(required = false) Integer days) {
    return AdminWithdrawalSummaryResponse.from(withdrawals.summary(days));
  }

  /** 탈퇴 취소 → 204. 변경 이력 USER_WITHDRAWAL_CANCEL. 탈퇴 예정이 아니면 404 */
  @PostMapping("/{userId}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@CurrentUser long adminId, @PathVariable String userId) {
    withdrawals.cancel(adminId, PathIds.parse(userId));
  }

  /** 바로 삭제 → 204 (되돌릴 수 없음). 변경 이력 USER_PURGE. 탈퇴 예정이 아니면 404 */
  @PostMapping("/{userId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void purge(@CurrentUser long adminId, @PathVariable String userId) {
    withdrawals.purge(adminId, PathIds.parse(userId));
  }
}
