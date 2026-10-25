package app.wearly.pairing.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 연결에서의 자리: 옷장 주인(OWNER) · 돕는 사람(HELPER). 코드를 만든 쪽, 연결을 끊은 쪽에도 쓴다. API 값과 DB 값이 같다 */
public enum PairingRole {
  OWNER,
  HELPER;

  public PairingRole other() {
    return this == OWNER ? HELPER : OWNER;
  }

  public static List<String> names() {
    return Arrays.stream(values()).map(Enum::name).toList();
  }

  /** 대소문자는 가리지 않는다. 모르는 값이면 빈 값 */
  public static Optional<PairingRole> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(r -> r.name().equals(v)).findFirst();
  }
}
