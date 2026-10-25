package app.wearly.invite.domain;

import java.util.Arrays;
import java.util.List;

/** 초대 코드 가입 결과 (tb_invite_signup.result) */
public enum InviteSignupResult {
  /** 추천인과 연결했다 */
  LINKED,
  /** 없는 코드라 추천인 없이 가입했다 */
  UNKNOWN_CODE;

  public static List<String> names() {
    return Arrays.stream(values()).map(Enum::name).toList();
  }
}
