package app.wearly.auth.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 로그인한 기기 종류 (16 문서 4장). 요청 헤더 {@code X-Wearly-Client: app|admin|web}로 정하고, 없거나 모르는 값이면 User-Agent로
 * 추정한다: 브라우저(Mozilla)면 WEB, 그 밖(React Native의 CFNetwork·okhttp, 서버 클라이언트)은 APP.
 */
public enum ClientKind {
  APP,
  ADMIN,
  WEB;

  public static ClientKind resolve(String header, String userAgent) {
    return parse(header).orElseGet(() -> guess(userAgent));
  }

  /** 대소문자 무시. 모르는 값이면 빈 값 */
  public static Optional<ClientKind> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    for (ClientKind k : values()) {
      if (k.name().equals(v)) {
        return Optional.of(k);
      }
    }
    return Optional.empty();
  }

  static ClientKind guess(String userAgent) {
    return userAgent != null && userAgent.contains("Mozilla/") ? WEB : APP;
  }
}
