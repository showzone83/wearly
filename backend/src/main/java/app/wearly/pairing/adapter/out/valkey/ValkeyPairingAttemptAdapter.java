package app.wearly.pairing.adapter.out.valkey;

import app.wearly.pairing.application.port.out.PairingAttemptPort;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * PairingAttemptPort 구현 (Valkey). wearly:pairing:join:&lt;회원&gt; = 시도 수. 첫 시도에서 window만큼 유지하고, 그 뒤
 * 사라지면 다시 센다 (고정 창)
 */
@Component
class ValkeyPairingAttemptAdapter implements PairingAttemptPort {

  static final String PREFIX = "wearly:pairing:join:";

  /** KEYS[1] = 시도 수. ARGV[1] = window 초. 더한 뒤의 시도 수 */
  static final RedisScript<Long> COUNT =
      new DefaultRedisScript<>(
          """
          local n = redis.call('INCR', KEYS[1])
          if n == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
          end
          return n
          """,
          Long.class);

  private final StringRedisTemplate redis;

  ValkeyPairingAttemptAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryAcquire(long userId, int limit, Duration window) {
    Long n =
        redis.execute(
            COUNT, List.of(PREFIX + userId), Long.toString(Math.max(1, window.toSeconds())));
    return n == null || n <= limit;
  }
}
