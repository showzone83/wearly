package app.wearly.auth.adapter.out.valkey;

import app.wearly.auth.application.port.out.SessionStorePort;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.LoginSession;
import app.wearly.auth.domain.RefreshToken;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SessionStorePort 구현 (Valkey, Lettuce). 키 구조는 {@link ValkeyKeys}, 여러 키를 바꾸는 동작은 {@link
 * ValkeyScripts}의 Lua로 원자적으로 한다. KEYS 명령은 쓰지 않는다: 목록은 정렬 집합 인덱스로 읽고, 만료된 멤버는 읽을 때 지운다.
 */
@Component
class ValkeySessionStoreAdapter implements SessionStorePort {

  private final StringRedisTemplate redis;

  ValkeySessionStoreAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void create(LoginSession s, RefreshToken token, Duration ttl) {
    long now = s.createdAt().toEpochMilli();
    redis.execute(
        ValkeyScripts.CREATE,
        List.of(
            ValkeyKeys.token(token.tokenHash()),
            ValkeyKeys.session(s.id()),
            ValkeyKeys.userSessions(s.userId()),
            ValkeyKeys.INDEX,
            ValkeyKeys.EXPIRY),
        token.tokenHash(),
        Long.toString(s.userId()),
        s.id(),
        Long.toString(now),
        Long.toString(now + ttl.toMillis()),
        s.client().name(),
        nullToEmpty(s.userAgent()),
        nullToEmpty(s.ip()),
        s.mfaVerified() ? "1" : "0");
  }

  @Override
  public Optional<RefreshToken> findToken(String tokenHash) {
    Map<String, String> f = hash(ValkeyKeys.token(tokenHash));
    if (f.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new RefreshToken(
            tokenHash,
            Long.parseLong(f.get("userId")),
            f.get("sessionId"),
            RefreshToken.Status.valueOf(f.get("status")),
            emptyToNull(f.get("replacedBy")),
            instant(f.get("createdAt"))));
  }

  @Override
  public boolean rotate(String currentHash, RefreshToken next, Instant now, Duration ttl) {
    long nowMs = now.toEpochMilli();
    Long done =
        redis.execute(
            ValkeyScripts.ROTATE,
            List.of(
                ValkeyKeys.token(currentHash),
                ValkeyKeys.token(next.tokenHash()),
                ValkeyKeys.INDEX,
                ValkeyKeys.EXPIRY),
            currentHash,
            next.tokenHash(),
            Long.toString(nowMs),
            Long.toString(nowMs + ttl.toMillis()),
            ValkeyKeys.PREFIX);
    return done != null && done == 1L;
  }

  @Override
  public Optional<LoginSession> findSession(String sessionId) {
    return toSession(sessionId, hash(ValkeyKeys.session(sessionId)));
  }

  @Override
  public SessionState touch(String sessionId, long userId, Instant now, Duration minInterval) {
    Long ok =
        redis.execute(
            ValkeyScripts.TOUCH,
            List.of(ValkeyKeys.session(sessionId), ValkeyKeys.INDEX),
            Long.toString(userId),
            Long.toString(now.toEpochMilli()),
            Long.toString(minInterval.toMillis()),
            sessionId);
    if (ok == null || ok == 0L) {
      return SessionState.GONE;
    }
    return ok == 2L ? SessionState.ACTIVE_MFA : SessionState.ACTIVE;
  }

  @Override
  public boolean markMfaVerified(String sessionId, long userId) {
    Long ok =
        redis.execute(
            ValkeyScripts.MARK_MFA, List.of(ValkeyKeys.session(sessionId)), Long.toString(userId));
    return ok != null && ok == 1L;
  }

  @Override
  public Optional<LoginSession> delete(String sessionId) {
    List<?> flat =
        redis.execute(
            ValkeyScripts.DELETE,
            List.of(ValkeyKeys.session(sessionId), ValkeyKeys.INDEX, ValkeyKeys.EXPIRY),
            sessionId,
            ValkeyKeys.PREFIX);
    Map<String, String> f = new HashMap<>();
    if (flat != null) {
      for (int i = 0; i + 1 < flat.size(); i += 2) {
        f.put(String.valueOf(flat.get(i)), String.valueOf(flat.get(i + 1)));
      }
    }
    return toSession(sessionId, f);
  }

  @Override
  public List<LoginSession> findByUser(long userId) {
    String key = ValkeyKeys.userSessions(userId);
    Set<String> ids = redis.opsForSet().members(key);
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<String> order = new ArrayList<>(ids);
    List<LoginSession> found = new ArrayList<>();
    List<String> gone = new ArrayList<>();
    load(order, found, gone);
    if (!gone.isEmpty()) {
      redis.opsForSet().remove(key, gone.toArray());
    }
    found.sort(Comparator.comparing(LoginSession::lastSeenAt).reversed());
    return found;
  }

  @Override
  public List<LoginSession> findAll(Instant now) {
    prune(now);
    return loadIndexed(range(0, -1));
  }

  @Override
  public SessionSlice findRecent(Instant now, long offset, int limit) {
    prune(now);
    Long total = redis.opsForZSet().zCard(ValkeyKeys.INDEX);
    List<LoginSession> items = loadIndexed(range(offset, offset + limit - 1));
    return new SessionSlice(items, total == null ? 0 : total);
  }

  @Override
  public long count(Instant now) {
    prune(now);
    Long total = redis.opsForZSet().zCard(ValkeyKeys.INDEX);
    return total == null ? 0 : total;
  }

  @Override
  public long countUsersSeenSince(Instant now, Instant since) {
    prune(now);
    Set<String> ids =
        redis
            .opsForZSet()
            .rangeByScore(ValkeyKeys.INDEX, since.toEpochMilli(), Double.POSITIVE_INFINITY);
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    List<String> order = new ArrayList<>(ids);
    List<Object> userIds =
        redis.executePipelined(
            (RedisCallback<Object>)
                conn -> {
                  for (String sid : order) {
                    conn.hashCommands().hGet(bytes(ValkeyKeys.session(sid)), bytes("userId"));
                  }
                  return null;
                });
    Set<Object> distinct = new HashSet<>(userIds);
    distinct.remove(null);
    return distinct.size();
  }

  // ── 내부 ──

  private void prune(Instant now) {
    redis.execute(
        ValkeyScripts.PRUNE,
        List.of(ValkeyKeys.INDEX, ValkeyKeys.EXPIRY),
        Long.toString(now.toEpochMilli()));
  }

  private List<String> range(long start, long end) {
    Set<String> ids = redis.opsForZSet().reverseRange(ValkeyKeys.INDEX, start, end);
    return ids == null ? List.of() : new ArrayList<>(ids);
  }

  /** 인덱스 순서대로 읽고, 그새 사라진 세션은 인덱스에서도 지운다 */
  private List<LoginSession> loadIndexed(List<String> order) {
    List<LoginSession> found = new ArrayList<>();
    List<String> gone = new ArrayList<>();
    load(order, found, gone);
    if (!gone.isEmpty()) {
      redis.opsForZSet().remove(ValkeyKeys.INDEX, gone.toArray());
      redis.opsForZSet().remove(ValkeyKeys.EXPIRY, gone.toArray());
    }
    return found;
  }

  /** 세션 해시를 한 번에(파이프라인) 읽는다 */
  @SuppressWarnings("unchecked")
  private void load(Collection<String> ids, List<LoginSession> found, List<String> gone) {
    if (ids.isEmpty()) {
      return;
    }
    List<String> order = new ArrayList<>(ids);
    List<Object> rows =
        redis.executePipelined(
            (RedisCallback<Object>)
                conn -> {
                  for (String sid : order) {
                    hashAll(conn, ValkeyKeys.session(sid));
                  }
                  return null;
                });
    for (int i = 0; i < order.size(); i++) {
      Object row = rows.get(i);
      Map<String, String> f = row instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of();
      Optional<LoginSession> s = toSession(order.get(i), f);
      if (s.isPresent()) {
        found.add(s.get());
      } else {
        gone.add(order.get(i));
      }
    }
  }

  private static void hashAll(RedisConnection conn, String key) {
    conn.hashCommands().hGetAll(bytes(key));
  }

  private Map<String, String> hash(String key) {
    Map<Object, Object> raw = redis.opsForHash().entries(key);
    Map<String, String> f = new HashMap<>();
    raw.forEach((k, v) -> f.put(String.valueOf(k), String.valueOf(v)));
    return f;
  }

  private static Optional<LoginSession> toSession(String sessionId, Map<String, String> f) {
    if (f.isEmpty() || f.get("userId") == null) {
      return Optional.empty();
    }
    return Optional.of(
        new LoginSession(
            sessionId,
            Long.parseLong(f.get("userId")),
            ClientKind.parse(f.get("client")).orElse(ClientKind.APP),
            emptyToNull(f.get("userAgent")),
            emptyToNull(f.get("ip")),
            instant(f.get("createdAt")),
            instant(f.get("lastRefreshAt")),
            instant(f.get("lastSeenAt")),
            "1".equals(f.get("mfa"))));
  }

  private static Instant instant(String epochMillis) {
    String v = emptyToNull(epochMillis);
    return v == null ? null : Instant.ofEpochMilli(Long.parseLong(v));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
