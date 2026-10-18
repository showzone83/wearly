package app.wearly.weather.adapter.out.valkey;

import app.wearly.weather.application.port.out.UsageLimitPort;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * UsageLimitPort 구현 (Valkey). 키 wearly:weather:usage:&lt;종류&gt;:&lt;날짜&gt;:&lt;회원&gt; = 그날 쓴 횟수 (이틀
 * 뒤 사라진다). 세기와 한도 확인을 스크립트 하나로 해서 동시에 와도 한도를 넘지 않는다.
 */
@Component
class ValkeyUsageLimitAdapter implements UsageLimitPort {

  static final String PREFIX = "wearly:weather:usage:";
  static final Duration KEEP = Duration.ofDays(2);

  /** ARGV[1] = 한도, ARGV[2] = 유지 초. 한도 안이면 1을 더하고 1, 아니면 0 */
  static final RedisScript<Long> CONSUME =
      new DefaultRedisScript<>(
          """
          local used = tonumber(redis.call('GET', KEYS[1]) or '0')
          if used >= tonumber(ARGV[1]) then
            return 0
          end
          redis.call('INCR', KEYS[1])
          redis.call('EXPIRE', KEYS[1], ARGV[2])
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redis;

  ValkeyUsageLimitAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryConsume(String kind, long userId, LocalDate day, int limit) {
    String key = PREFIX + kind + ":" + day + ":" + userId;
    Long r =
        redis.execute(
            CONSUME, List.of(key), Integer.toString(limit), Long.toString(KEEP.toSeconds()));
    return r != null && r == 1L;
  }
}
