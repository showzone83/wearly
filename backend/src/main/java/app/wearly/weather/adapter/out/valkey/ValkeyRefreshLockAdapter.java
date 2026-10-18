package app.wearly.weather.adapter.out.valkey;

import app.wearly.weather.application.port.out.RefreshLockPort;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** RefreshLockPort 구현 (Valkey). 키 wearly:weather:refresh:lock = 실행마다 다른 owner 값 */
@Component
class ValkeyRefreshLockAdapter implements RefreshLockPort {

  static final String KEY = "wearly:weather:refresh:lock";

  /** 내 값일 때만 지운다 (실행이 ttl보다 오래 걸려 다른 실행이 잡은 잠금을 지우지 않게) */
  static final RedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
          end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redis;

  ValkeyRefreshLockAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryAcquire(String owner, Duration ttl) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, owner, ttl));
  }

  @Override
  public void release(String owner) {
    redis.execute(RELEASE, List.of(KEY), owner);
  }

  @Override
  public boolean held() {
    return Boolean.TRUE.equals(redis.hasKey(KEY));
  }
}
