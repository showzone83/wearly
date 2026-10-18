package app.wearly.notify.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.notify.adapter.in.web.request.PushTokenRequest;
import app.wearly.notify.adapter.in.web.response.PushTokenResponse;
import app.wearly.notify.application.port.in.PushTokenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 앱 푸시 토큰 등록·삭제 (로그인 필요). 회원·기기당 하나 */
@RestController
@RequestMapping("/api/v1/me/push-token")
public class PushTokenController {

  private final PushTokenUseCase tokens;

  public PushTokenController(PushTokenUseCase tokens) {
    this.tokens = tokens;
  }

  /** 등록 (같은 기기면 바꾼다). 200 { deviceId, platform, serverPush, dayBeforeTime } */
  @PutMapping
  public PushTokenResponse register(
      @CurrentUser long userId, @RequestBody PushTokenRequest request) {
    return PushTokenResponse.from(tokens.register(userId, request.toDto()));
  }

  /** 이 기기의 토큰 삭제 (로그아웃 전에 앱이 부른다). 없어도 204 */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(
      @CurrentUser long userId, @RequestParam(required = false) String deviceId) {
    tokens.unregister(userId, deviceId);
  }
}
