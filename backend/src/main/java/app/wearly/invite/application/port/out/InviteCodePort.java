package app.wearly.invite.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** 회원별 초대 코드 (tb_user_invite_code, JPA) */
public interface InviteCodePort {

  Optional<String> findCode(long userId);

  /** 코드의 주인. 주인이 탈퇴 예정(doc/27)이면 빈 값 — 가입 때 추천인으로 잇지 않는다 */
  Optional<Long> findUserId(String code);

  /**
   * 회원에게 코드가 없을 때만 넣는다. 이미 코드가 있거나(동시에 연 경우) 같은 코드가 다른 회원에게 있으면 넣지 않는다 (트랜잭션을 깨지 않는다). 넣었으면 true
   */
  boolean insertIfAbsent(long userId, String code, Instant now);
}
