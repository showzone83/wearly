package app.wearly.pairing.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.pairing.adapter.in.web.request.ApprovePairingRequest;
import app.wearly.pairing.adapter.in.web.request.JoinPairingRequest;
import app.wearly.pairing.adapter.in.web.request.PairingCodeRequest;
import app.wearly.pairing.adapter.in.web.request.UpdatePairingRequest;
import app.wearly.pairing.adapter.in.web.response.PairingCodeResponse;
import app.wearly.pairing.adapter.in.web.response.PairingListResponse;
import app.wearly.pairing.adapter.in.web.response.PairingResponse;
import app.wearly.pairing.adapter.in.web.response.ProxyActionResponse;
import app.wearly.pairing.application.port.in.PairingUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 옷장 도우미 연결 (doc/26 4.3, 로그인 필요): 코드 발급·입력, 목록, 승인·거절, 권한·알림 변경, 해제 */
@RestController
@RequestMapping("/api/v1/pairings")
public class PairingController {

  private final PairingUseCase pairings;

  public PairingController(PairingUseCase pairings) {
    this.pairings = pairings;
  }

  /** 201 { code, role, expiresAt }. 옷장 주인 코드만 만든다(#18). 이전 코드는 사라진다. 본문은 비워도 된다 */
  @PostMapping("/codes")
  @ResponseStatus(HttpStatus.CREATED)
  public PairingCodeResponse issueCode(
      @CurrentUser long userId, @RequestBody(required = false) PairingCodeRequest request) {
    return PairingCodeResponse.from(
        pairings.issueCode(userId, request == null ? null : request.role()));
  }

  /** 201 연결 (PENDING, 주인 승인을 기다린다) */
  @PostMapping("/join")
  @ResponseStatus(HttpStatus.CREATED)
  public PairingResponse join(@CurrentUser long userId, @RequestBody JoinPairingRequest request) {
    return PairingResponse.from(pairings.join(userId, request.toDto()));
  }

  /** 200 { asOwner: [...], asHelper: [...] } */
  @GetMapping
  public PairingListResponse list(@CurrentUser long userId) {
    return PairingListResponse.from(pairings.list(userId));
  }

  /** 주인이 대기 요청 승인 { permission } → 200 연결 */
  @PostMapping("/{id}/approve")
  public PairingResponse approve(
      @CurrentUser long userId,
      @PathVariable String id,
      @RequestBody ApprovePairingRequest request) {
    return PairingResponse.from(pairings.approve(userId, PathIds.parse(id), request.permission()));
  }

  /** 주인이 대기 요청 거절 → 204 */
  @PostMapping("/{id}/reject")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reject(@CurrentUser long userId, @PathVariable String id) {
    pairings.reject(userId, PathIds.parse(id));
  }

  /** 주인이 권한·알림 변경 { permission?, notifyOwner? } → 200 연결 */
  @PatchMapping("/{id}")
  public PairingResponse update(
      @CurrentUser long userId,
      @PathVariable String id,
      @RequestBody UpdatePairingRequest request) {
    return PairingResponse.from(pairings.update(userId, PathIds.parse(id), request.toDto()));
  }

  /** 연결 해제 (양쪽 모두, 대기 취소 포함) → 204. 옷은 주인 옷장에 남는다 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void end(@CurrentUser long userId, @PathVariable String id) {
    pairings.end(userId, PathIds.parse(id));
  }

  /** 200 [{ id, action, itemId, itemName, createdAt }] 돕는 사람이 한 일, 최근 순 최대 100건 (주인·돕는 사람 모두) */
  @GetMapping("/{id}/actions")
  public List<ProxyActionResponse> actions(@CurrentUser long userId, @PathVariable String id) {
    return pairings.actions(userId, PathIds.parse(id)).stream()
        .map(ProxyActionResponse::from)
        .toList();
  }
}
