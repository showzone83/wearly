package app.wearly.invite.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 초대를 보낸 방법 (tb_invite_share.channel). API 값과 DB 값이 같다 */
public enum InviteChannel {
  /** OS 공유 시트 (카카오톡·메시지 등 설치된 앱) */
  SHARE,
  /** 문자 */
  SMS,
  /** 코드 복사 */
  COPY;

  public static List<String> names() {
    return Arrays.stream(values()).map(Enum::name).toList();
  }

  /** 대소문자는 가리지 않는다. 모르는 값이면 빈 값 */
  public static Optional<InviteChannel> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(c -> c.name().equals(v)).findFirst();
  }
}
