package app.wearly.ai.application.service;

import app.wearly.ai.application.port.out.AiReservationPort;
import app.wearly.ai.application.port.out.AiUsagePort;
import app.wearly.common.domain.Zones;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능별 일일 한도 (M7). 한도 기준은 오늘(한국 시간) 성공한 호출 수이고, AI를 부르기 전에 회원·기능별 잠금 안에서 "성공 + 진행 중 예약 &lt; 한도"일 때만
 * 예약을 넣는다. 그래서 같은 회원이 동시에 여러 번 불러도 한도를 넘지 않는다. 호출이 끝나면(성공·실패 모두) release로 예약을 지운다. 성공 기록과 예약 삭제
 * 사이에는 둘 다 세므로 한도를 넘는 쪽이 아니라 잠깐 덜 주는 쪽으로 틀린다.
 */
@Service
public class AiQuotaService {

  /** 이보다 오래된 예약은 서버가 죽어 남은 것으로 보고 세지 않는다 (공급자 시간 제한 합보다 넉넉히) */
  static final Duration STALE = Duration.ofMinutes(30);

  private final AiUsagePort usage;
  private final AiReservationPort reservations;
  private final Clock clock;

  public AiQuotaService(AiUsagePort usage, AiReservationPort reservations, Clock clock) {
    this.usage = usage;
    this.reservations = reservations;
    this.clock = clock;
  }

  /**
   * 예약.
   *
   * @param id 예약 ID
   * @param used 이 호출까지 센 오늘 사용 수 (성공 + 진행 중)
   */
  public record Reservation(long id, long used) {}

  /** 한도 안이면 예약을 넣는다. 다 썼으면 빈 값 */
  @Transactional
  public Optional<Reservation> reserve(long userId, String feature, int limit) {
    usage.lockQuota(userId, feature);
    Instant now = clock.instant();
    Instant stale = now.minus(STALE);
    reservations.deleteOlderThan(userId, feature, stale);
    long used = successToday(userId, feature) + usage.countReservations(userId, feature, stale);
    if (used >= limit) {
      return Optional.empty();
    }
    return Optional.of(new Reservation(reservations.reserve(userId, feature, now), used + 1));
  }

  @Transactional
  public void release(Reservation reservation) {
    reservations.release(reservation.id());
  }

  /** 오늘(한국 시간) 그 회원이 그 기능으로 성공한 호출 수 */
  @Transactional(readOnly = true)
  public long successToday(long userId, String feature) {
    LocalDate today = Zones.today(clock);
    return usage.countSuccess(
        userId, feature, Zones.startOf(today), Zones.startOf(today.plusDays(1)));
  }
}
