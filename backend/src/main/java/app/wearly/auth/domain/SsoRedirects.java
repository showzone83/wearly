package app.wearly.auth.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SSO 돌아갈 주소 다루기 (22 문서 4.2). 관리자 사이트 주소는 허용 목록과 정확히 같아야 한다(열린 리디렉트 방지). 비교 전에 scheme·host를 소문자로,
 * 기본 포트를 빼고, 빈 경로를 "/"로 맞춘다. 쿼리·조각(#)·사용자 정보가 있는 주소는 받지 않는다.
 */
public final class SsoRedirects {

  private SsoRedirects() {}

  /** http·https 절대 주소만. 받을 수 없으면 빈 값 */
  public static Optional<String> normalize(String url) {
    if (url == null || url.isBlank() || url.length() > 2000) {
      return Optional.empty();
    }
    URI u;
    try {
      u = new URI(url.strip());
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return Optional.empty();
    }
    if (u.getHost() == null
        || u.getRawUserInfo() != null
        || u.getRawQuery() != null
        || u.getRawFragment() != null) {
      return Optional.empty();
    }
    int port = u.getPort();
    boolean defaultPort =
        port == -1
            || ("http".equals(scheme) && port == 80)
            || ("https".equals(scheme) && port == 443);
    String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
    return Optional.of(
        scheme
            + "://"
            + u.getHost().toLowerCase(Locale.ROOT)
            + (defaultPort ? "" : ":" + port)
            + path);
  }

  /** 주소 뒤에 쿼리 값을 붙인다 (값이 null이면 뺀다) */
  public static String withQuery(String base, Map<String, String> params) {
    String q =
        params.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .collect(Collectors.joining("&"));
    if (q.isEmpty()) {
      return base;
    }
    return base + (base.contains("?") ? "&" : "?") + q;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
