package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.response.AdminPairingActionResponse;
import app.wearly.admin.adapter.in.web.response.AdminPairingResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.application.port.in.AdminPairingUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 옷장 도우미 API (doc/26 6단계, 07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/pairings")
public class AdminPairingController {

  private final AdminPairingUseCase pairings;

  public AdminPairingController(AdminPairingUseCase pairings) {
    this.pairings = pairings;
  }

  /** 연결 목록 { items, page, size, total } 최근 요청순. status: PENDING · ACTIVE · ENDED */
  @GetMapping
  public PageResponse<AdminPairingResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        pairings.list(q, status, PagingDto.of(page, size)), AdminPairingResponse::from);
  }

  /** 연결의 대리 작업 기록 [{ id, action, itemId, itemName, createdAt }] 최신순 최대 100건 */
  @GetMapping("/{id}/actions")
  public List<AdminPairingActionResponse> actions(@PathVariable String id) {
    return pairings.actions(PathIds.parse(id)).stream()
        .map(AdminPairingActionResponse::from)
        .toList();
  }

  /** 연결 강제 해제 → 204. 변경 이력 PAIRING_FORCE_END. 없거나 이미 끝났으면 404 */
  @PostMapping("/{id}/end")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void end(@CurrentUser long adminId, @PathVariable String id) {
    pairings.forceEnd(adminId, PathIds.parse(id));
  }
}
