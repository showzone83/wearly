package app.wearly.notify.adapter.out.persistence.jpa;

import app.wearly.notify.application.port.out.PushSendLogPort;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PushSendLogPort 구현 (JPA, 넣기는 네이티브 SQL) */
@Component
class PushSendLogJpaAdapter implements PushSendLogPort {

  static final String DAY_BEFORE = "DAY_BEFORE";

  private final PushSendLogJpaRepository repository;

  PushSendLogJpaAdapter(PushSendLogJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public boolean claim(long planId, long userId, LocalDate plannedDate, LocalDate sendDate) {
    return repository.claim(planId, userId, DAY_BEFORE, plannedDate, sendDate) > 0;
  }

  @Override
  @Transactional
  public void release(long planId, LocalDate sendDate) {
    repository.release(planId, DAY_BEFORE, sendDate);
  }

  @Override
  @Transactional
  public int purgeBefore(Instant cutoff) {
    return repository.deleteBefore(cutoff);
  }
}
