package app.wearly.weather.application.port.out;

import java.time.Duration;

/** 실행 잠금 (Valkey SET NX EX). 서버가 여러 대여도 한 번에 실행 하나만 돈다. 실행이 멈춰도 ttl이 지나면 풀린다. */
public interface RefreshLockPort {

  /** 잡으면 true. owner는 실행마다 다른 값 */
  boolean tryAcquire(String owner, Duration ttl);

  /** owner가 잡은 잠금일 때만 푼다 */
  void release(String owner);

  /** 지금 누군가 잡고 있는지 */
  boolean held();
}
