package app.wearly.weather.application.port.out;

import java.time.Duration;

/**
 * 이름별 잠금 (Valkey SET NX EX). 같은 격자의 예보·평년값을 여러 요청이 동시에 받지 않게 한다 (서버가 여러 대여도). 요청이 멈춰도 ttl이 지나면 풀린다.
 */
public interface WeatherLockPort {

  /**
   * @param name 예: place:37.6,127.0 · climate:37.6,127.0
   * @return 잡으면 true
   */
  boolean tryAcquire(String name, String owner, Duration ttl);

  /** owner가 잡은 잠금일 때만 푼다 */
  void release(String name, String owner);
}
