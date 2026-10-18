package app.wearly.notify.domain;

import java.util.Locale;
import java.util.Optional;

/** 푸시 토큰을 등록한 기기 종류. API 값은 소문자(ios·android), DB 값은 대문자 */
public enum PushPlatform {
  IOS,
  ANDROID;

  /** API 값 (ios · android) */
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** API 값 → 종류. 대소문자는 가리지 않는다. 모르는 값이면 빈 값 */
  public static Optional<PushPlatform> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    for (PushPlatform p : values()) {
      if (p.name().equalsIgnoreCase(value.strip())) {
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }
}
