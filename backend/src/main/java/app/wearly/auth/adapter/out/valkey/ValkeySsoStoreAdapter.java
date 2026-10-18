package app.wearly.auth.adapter.out.valkey;

import app.wearly.auth.application.port.out.SsoStorePort;
import app.wearly.auth.domain.SsoGrant;
import app.wearly.auth.domain.SsoState;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SsoStorePort 구현 (Valkey). 값은 문자열 하나(form 인코딩 k=v&amp;…)로 두고 꺼낼 때 GETDEL로 읽고 지운다 — 같은 state·교환 코드를
 * 두 번 쓸 수 없다(동시에 와도 한쪽만 받는다). 키 이름은 {@link ValkeyKeys}.
 */
@Component
class ValkeySsoStoreAdapter implements SsoStorePort {

  private final StringRedisTemplate redis;

  ValkeySsoStoreAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void saveState(String stateHash, SsoState s, Duration ttl) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("provider", s.providerId());
    f.put("nonce", s.nonce());
    f.put("verifier", s.codeVerifier());
    f.put("redirect", s.redirect());
    f.put("callback", s.callbackUri());
    f.put("createdAt", Long.toString(s.createdAt().toEpochMilli()));
    redis.opsForValue().set(ValkeyKeys.ssoState(stateHash), encode(f), ttl);
  }

  @Override
  public Optional<SsoState> takeState(String stateHash) {
    return take(ValkeyKeys.ssoState(stateHash))
        .filter(f -> f.get("provider") != null && f.get("nonce") != null)
        .map(
            f ->
                new SsoState(
                    f.get("provider"),
                    f.get("nonce"),
                    f.get("verifier"),
                    f.get("redirect"),
                    f.get("callback"),
                    Instant.ofEpochMilli(Long.parseLong(f.getOrDefault("createdAt", "0")))));
  }

  @Override
  public void saveGrant(String codeHash, SsoGrant g, Duration ttl) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("userId", Long.toString(g.userId()));
    f.put("provider", g.providerId());
    if (g.amr() != null) {
      f.put("amr", g.amr());
    }
    f.put("createdAt", Long.toString(g.createdAt().toEpochMilli()));
    redis.opsForValue().set(ValkeyKeys.ssoGrant(codeHash), encode(f), ttl);
  }

  @Override
  public Optional<SsoGrant> takeGrant(String codeHash) {
    return take(ValkeyKeys.ssoGrant(codeHash))
        .filter(f -> f.get("userId") != null)
        .map(
            f ->
                new SsoGrant(
                    Long.parseLong(f.get("userId")),
                    f.get("provider"),
                    f.get("amr"),
                    Instant.ofEpochMilli(Long.parseLong(f.getOrDefault("createdAt", "0")))));
  }

  private Optional<Map<String, String>> take(String key) {
    String raw = redis.opsForValue().getAndDelete(key);
    return raw == null ? Optional.empty() : Optional.of(decode(raw));
  }

  private static String encode(Map<String, String> f) {
    return f.entrySet().stream()
        .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static Map<String, String> decode(String raw) {
    Map<String, String> f = new HashMap<>();
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        f.put(dec(pair.substring(0, eq)), dec(pair.substring(eq + 1)));
      }
    }
    return f;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
  }

  private static String dec(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }
}
