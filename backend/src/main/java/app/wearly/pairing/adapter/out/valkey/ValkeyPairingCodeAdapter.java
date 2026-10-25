package app.wearly.pairing.adapter.out.valkey;

import app.wearly.pairing.application.port.out.PairingCodePort;
import app.wearly.pairing.domain.PairingCode;
import app.wearly.pairing.domain.PairingRole;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * PairingCodePort 구현 (Valkey). 두 키를 스크립트로 함께 다뤄 회원·자리마다 코드가 하나만 남게 한다.
 *
 * <ul>
 *   <li>wearly:pairing:code:&lt;코드&gt; = "&lt;회원&gt;:&lt;자리&gt;" (ttl 뒤 사라진다)
 *   <li>wearly:pairing:issuer:&lt;회원&gt;:&lt;자리&gt; = 그 회원의 지금 코드 (같은 ttl)
 * </ul>
 */
@Component
class ValkeyPairingCodeAdapter implements PairingCodePort {

  static final String CODE_PREFIX = "wearly:pairing:code:";
  static final String ISSUER_PREFIX = "wearly:pairing:issuer:";

  /**
   * KEYS[1] = 새 코드, KEYS[2] = 회원의 지금 코드. ARGV[1] = 값, ARGV[2] = ttl 초, ARGV[3] = 코드 키 앞부분, ARGV[4]
   * = 새 코드. 같은 코드가 있으면 0, 두었으면 1
   */
  static final RedisScript<Long> ISSUE =
      new DefaultRedisScript<>(
          """
          if redis.call('EXISTS', KEYS[1]) == 1 then
            return 0
          end
          local old = redis.call('GET', KEYS[2])
          if old then
            redis.call('DEL', ARGV[3] .. old)
          end
          redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
          redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[2])
          return 1
          """,
          Long.class);

  /** KEYS[1] = 코드, KEYS[2] = 회원의 지금 코드. ARGV[1] = 기대한 값, ARGV[2] = 코드. 지웠으면 1, 값이 다르면 0 */
  static final RedisScript<Long> TAKE =
      new DefaultRedisScript<>(
          """
          if redis.call('GET', KEYS[1]) ~= ARGV[1] then
            return 0
          end
          redis.call('DEL', KEYS[1])
          if redis.call('GET', KEYS[2]) == ARGV[2] then
            redis.call('DEL', KEYS[2])
          end
          return 1
          """,
          Long.class);

  /**
   * KEYS = 회원의 자리별 지금 코드 키들. ARGV[1] = 코드 키 앞부분, ARGV[i + 1] = KEYS[i] 자리의 값. 코드가 아직 그 회원 것이면 함께
   * 지운다
   */
  static final RedisScript<Long> REVOKE =
      new DefaultRedisScript<>(
          """
          for i, key in ipairs(KEYS) do
            local code = redis.call('GET', key)
            if code and redis.call('GET', ARGV[1] .. code) == ARGV[i + 1] then
              redis.call('DEL', ARGV[1] .. code)
            end
            redis.call('DEL', key)
          end
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redis;

  ValkeyPairingCodeAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean issue(String code, PairingCode value, Duration ttl) {
    Long r =
        redis.execute(
            ISSUE,
            List.of(CODE_PREFIX + code, issuerKey(value)),
            format(value),
            Long.toString(Math.max(1, ttl.toSeconds())),
            CODE_PREFIX,
            code);
    return r != null && r == 1;
  }

  @Override
  public Optional<PairingCode> peek(String code) {
    return parse(redis.opsForValue().get(CODE_PREFIX + code));
  }

  @Override
  public boolean take(String code, PairingCode value) {
    Long r =
        redis.execute(TAKE, List.of(CODE_PREFIX + code, issuerKey(value)), format(value), code);
    return r != null && r == 1;
  }

  @Override
  public void revokeAll(long issuerId) {
    List<String> keys = new ArrayList<>();
    List<String> args = new ArrayList<>(List.of(CODE_PREFIX));
    for (PairingRole role : PairingRole.values()) {
      PairingCode value = new PairingCode(issuerId, role);
      keys.add(issuerKey(value));
      args.add(format(value));
    }
    redis.execute(REVOKE, keys, args.toArray());
  }

  private static String issuerKey(PairingCode value) {
    return ISSUER_PREFIX + value.issuerId() + ":" + value.issuerRole().name();
  }

  static String format(PairingCode value) {
    return value.issuerId() + ":" + value.issuerRole().name();
  }

  static Optional<PairingCode> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    int colon = raw.indexOf(':');
    if (colon < 0) {
      return Optional.empty();
    }
    try {
      long issuerId = Long.parseLong(raw.substring(0, colon));
      return PairingRole.parse(raw.substring(colon + 1)).map(r -> new PairingCode(issuerId, r));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
