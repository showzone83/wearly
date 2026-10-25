package app.wearly.invite.application.port.in;

import app.wearly.invite.application.port.in.dto.InviteDto;
import app.wearly.invite.application.port.in.dto.InvitedFriendDto;
import java.util.List;

/** 친구 초대: 내 초대 코드, 초대한 이력, 초대 코드 가입 이력 (doc/25) */
public interface InviteUseCase {

  /** 내 초대 코드와 그 코드로 가입한 친구 수. 코드가 없으면 만든다 (처음 한 번) */
  InviteDto myInvite(long userId);

  /**
   * 초대한 이력을 남긴다 (공유 시트·문자·코드 복사). 같은 방법은 60초에 한 번, 하루 30건까지만 남기고 넘으면 기록 없이 성공한다 (InviteSharePolicy,
   * doc/25 6장).
   *
   * <p>오류: 400 VALIDATION_FAILED (channel NOT_ALLOWED: SHARE · SMS · COPY)
   */
  void recordShare(long userId, String channel);

  /** 내 초대 코드로 가입한 친구 (최근 가입순, 최대 100명) */
  List<InvitedFriendDto> invitedFriends(long userId);

  /**
   * 가입할 때 입력한 초대 코드를 남긴다 (가입과 같은 트랜잭션). 코드가 비어 있으면 아무것도 하지 않는다. 코드가 있으면 추천인과 연결(LINKED)하고, 없는 코드면
   * 추천인 없이 UNKNOWN_CODE로 남긴다. 가입을 막지는 않는다.
   */
  void recordSignup(long newUserId, String enteredCode);
}
