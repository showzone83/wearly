package app.wearly.weather.adapter.out.valkey;

import app.wearly.weather.application.port.out.WeatherLockPort;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** WeatherLockPort 구현 (Valkey). 키 wearly:weather:lock:&lt;이름&gt; = 요청마다 다른 owner 값 */
@Component
class ValkeyWeatherLockAdapter implements WeatherLockPort {

  static final String PREFIX = "wearly:weather:lock:";

  private final StringRedisTemplate redis;

  ValkeyWeatherLockAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryAcquire(String name, String owner, Duration ttl) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + name, owner, ttl));
  }

  @Override
  public void release(String name, String owner) {
    // 내 값일 때만 지운다 (ttl이 지나 다른 요청이 잡은 잠금을 지우지 않게)
    redis.execute(ValkeyRefreshLockAdapter.RELEASE, List.of(PREFIX + name), owner);
  }
}
