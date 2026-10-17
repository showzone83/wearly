package app.wearly.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 엔티티 시각은 주입한 Clock을 따른다 (L9, Spring 없이) */
class PersistenceClockTest {

  @Test
  void 주입한_시계로_시각을_준다() {
    Instant fixed = Instant.parse("2026-10-17T00:00:00Z");
    try {
      new PersistenceClock(Clock.fixed(fixed, ZoneOffset.UTC));
      assertThat(PersistenceClock.now()).isEqualTo(fixed);
    } finally {
      new PersistenceClock(Clock.systemUTC());
    }
  }
}
