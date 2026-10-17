package app.wearly.common.persistence;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * JPA 엔티티의 created_at·updated_at 시각 출처 (L9). 엔티티 콜백(@PrePersist·@PreUpdate)은 Spring 빈을 주입받지 못하므로,
 * 시작할 때 주입한 Clock을 여기 담아 두고 엔티티는 {@link #now()}를 쓴다. 그래서 한도·통계 구간을 계산하는 Clock과 기록 시각이 같은 시계를 본다.
 */
@Component
public final class PersistenceClock {

  private static volatile Clock clock = Clock.systemUTC();

  public PersistenceClock(Clock clock) {
    PersistenceClock.clock = clock;
  }

  public static Instant now() {
    return clock.instant();
  }
}
