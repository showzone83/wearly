package app.wearly.pairing.application.port.out;

import java.time.Duration;

/** 코드 입력 시도 한도 (Valkey) */
public interface PairingAttemptPort {

  /** 시도를 하나 세고, window 안에서 limit번 이하면 true */
  boolean tryAcquire(long userId, int limit, Duration window);
}
