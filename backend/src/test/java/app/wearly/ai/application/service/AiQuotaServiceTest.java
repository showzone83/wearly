package app.wearly.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.application.port.out.AiReservationPort;
import app.wearly.ai.application.port.out.AiUsagePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 일일 한도 예약 규칙 (가짜 포트, Spring 없이). 동시성은 AiQuotaConcurrencyIntegrationTest가 실제 DB로 본다 */
class AiQuotaServiceTest {

  /** 한국 시간 2026-10-17 09:00 */
  static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

  final List<String> calls = new ArrayList<>();
  final Map<Long, Instant> reservations = new LinkedHashMap<>();
  long success;
  Instant countedFrom;
  Instant countedTo;
  long nextId = 1;

  final AiUsagePort usage =
      new AiUsagePort() {
        @Override
        public long countSuccess(long userId, String feature, Instant from, Instant to) {
          countedFrom = from;
          countedTo = to;
          return success;
        }

        @Override
        public long countReservations(long userId, String feature, Instant since) {
          return reservations.values().stream().filter(at -> !at.isBefore(since)).count();
        }

        @Override
        public void lockQuota(long userId, String feature) {
          calls.add("lock " + userId + " " + feature);
        }
      };

  final AiReservationPort reservationPort =
      new AiReservationPort() {
        @Override
        public long reserve(long userId, String feature, Instant at) {
          calls.add("reserve");
          reservations.put(nextId, at);
          return nextId++;
        }

        @Override
        public void release(long reservationId) {
          reservations.remove(reservationId);
        }

        @Override
        public void deleteOlderThan(long userId, String feature, Instant before) {
          calls.add("cleanup " + before);
          reservations.values().removeIf(at -> at.isBefore(before));
        }
      };

  final AiQuotaService quota =
      new AiQuotaService(usage, reservationPort, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 잠금을_먼저_잡고_성공과_진행_중_예약을_함께_센다() {
    success = 1;

    Optional<AiQuotaService.Reservation> first = quota.reserve(3L, "ITEM_ANALYZE", 3);
    Optional<AiQuotaService.Reservation> second = quota.reserve(3L, "ITEM_ANALYZE", 3);
    Optional<AiQuotaService.Reservation> third = quota.reserve(3L, "ITEM_ANALYZE", 3);

    assertThat(first).map(AiQuotaService.Reservation::used).hasValue(2L);
    assertThat(second).map(AiQuotaService.Reservation::used).hasValue(3L);
    assertThat(third).isEmpty();
    assertThat(calls.getFirst()).isEqualTo("lock 3 ITEM_ANALYZE");
    // 성공 수는 한국 시간 오늘 하루로 센다
    assertThat(countedFrom).isEqualTo(Instant.parse("2026-10-16T15:00:00Z"));
    assertThat(countedTo).isEqualTo(Instant.parse("2026-10-17T15:00:00Z"));

    quota.release(first.orElseThrow());
    assertThat(quota.reserve(3L, "ITEM_ANALYZE", 3)).isPresent();
  }

  @Test
  void 오래된_예약은_지우고_세지_않는다() {
    reservations.put(nextId++, NOW.minus(AiQuotaService.STALE).minusSeconds(1));
    reservations.put(nextId++, NOW.minus(AiQuotaService.STALE).minusSeconds(1));

    assertThat(quota.reserve(3L, "ITEM_ANALYZE", 1)).isPresent();
    assertThat(calls).contains("cleanup " + NOW.minus(AiQuotaService.STALE));
    assertThat(reservations).hasSize(1);
  }

  @Test
  void 한도가_0이면_예약하지_않는다() {
    assertThat(quota.reserve(3L, "ITEM_ANALYZE", 0)).isEmpty();
    assertThat(calls).doesNotContain("reserve");
  }
}
