package app.wearly.notify.application.port.out;

import app.wearly.notify.domain.PushPlatform;
import java.time.Instant;
import java.util.Collection;

/** 푸시 토큰 쓰기 (JPA) */
public interface PushTokenPort {

  /**
   * 회원·기기의 토큰을 저장한다. 그 회원·기기의 행이 있으면 토큰·종류를 바꾸고, 없으면 만든다. 같은 토큰이 다른 회원·기기에 있으면 그 행은 지운다 (한 기기에서 계정을
   * 바꿔 로그인한 경우).
   */
  void upsert(long userId, String deviceId, PushPlatform platform, String token, Instant now);

  /** 회원·기기의 토큰을 지운다. 없으면 아무것도 하지 않는다 */
  void delete(long userId, String deviceId);

  /** 회원의 토큰을 모든 기기에서 지운다 (탈퇴 신청, doc/27). 지운 행 수 */
  int deleteAllOf(long userId);

  /** 이 토큰들을 지운다 (Expo가 DeviceNotRegistered로 답한 토큰). 지운 행 수 */
  int deleteTokens(Collection<String> tokens);
}
