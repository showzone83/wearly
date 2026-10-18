package app.wearly.weather.application.service;

import app.wearly.weather.application.port.out.WeatherLockPort;
import app.wearly.weather.domain.PlaceWeatherOptions;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 격자별 잠금 (19 문서 3·4장): 같은 격자의 예보·평년값을 여러 요청이 동시에 받지 않게 한다. 잠금을 못 잡으면 다른 요청이 받기를 끝낼 때까지 조금씩 기다린다.
 */
@Service
public class WeatherLocks {

  private static final Logger log = LoggerFactory.getLogger(WeatherLocks.class);

  /** 기다리는 동안 다시 확인하는 간격 */
  static final Duration POLL = Duration.ofMillis(50);

  private final WeatherLockPort port;

  public WeatherLocks(WeatherLockPort port) {
    this.port = port;
  }

  /**
   * 잠금을 잡는다. 다른 요청이 잡고 있으면 done이 참이 되거나(그 요청이 받기를 끝냄) wait가 지날 때까지 기다린다.
   *
   * @return 잡았으면 owner(풀 때 넘긴다), 못 잡았으면 null
   */
  String acquireOrWait(String name, Duration wait, BooleanSupplier done) {
    String owner = UUID.randomUUID().toString();
    long deadline = System.nanoTime() + wait.toNanos();
    while (true) {
      if (port.tryAcquire(name, owner, PlaceWeatherOptions.LOCK_TTL)) {
        return owner;
      }
      if (done.getAsBoolean() || System.nanoTime() >= deadline) {
        return null;
      }
      try {
        Thread.sleep(POLL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  void release(String name, String owner) {
    if (owner == null) {
      return;
    }
    try {
      port.release(name, owner);
    } catch (RuntimeException e) {
      // 풀지 못해도 LOCK_TTL이 지나면 풀린다
      log.warn("날씨 잠금 {}을 풀지 못했어요 ({})", name, e.getClass().getSimpleName());
    }
  }
}
