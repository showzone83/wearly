package app.wearly.user.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 탈퇴 사유 (tb_user_withdrawal.reason, V30 CHECK와 같다). 앱이 보기로 보여 주고 회원은 하나를 꼭 고른다 (doc/27 W4) */
public enum WithdrawalReason {
  /** 자주 안 써요 */
  NOT_USEFUL,
  /** 추천이 마음에 안 들어요 */
  BAD_RECOMMEND,
  /** 쓰기 어려워요 */
  HARD_TO_USE,
  /** 개인정보가 걱정돼요 */
  PRIVACY,
  /** 다른 앱을 쓸래요 */
  OTHER_APP,
  /** 기타 */
  OTHER;

  public static List<String> names() {
    return Arrays.stream(values()).map(Enum::name).toList();
  }

  /** 정확한 이름만 받는다 */
  public static Optional<WithdrawalReason> parse(String raw) {
    return Arrays.stream(values()).filter(r -> r.name().equals(raw)).findFirst();
  }
}
