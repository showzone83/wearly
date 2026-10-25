package app.wearly.user.adapter.out.persistence.jpa;

import app.wearly.user.application.port.out.WithdrawalPort;
import app.wearly.user.domain.Withdrawal;
import app.wearly.user.domain.Withdrawal.Closer;
import app.wearly.user.domain.WithdrawalReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/** WithdrawalPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class WithdrawalJpaAdapter implements WithdrawalPort {

  private final WithdrawalJpaRepository repository;

  WithdrawalJpaAdapter(WithdrawalJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Withdrawal save(Withdrawal w) {
    WithdrawalEntity e =
        w.id() == null
            ? new WithdrawalEntity(
                w.userId(), w.reason().name(), w.reasonText(), w.requestedAt(), w.purgeAfter())
            : repository
                .findById(w.id())
                .orElseThrow(() -> new IllegalStateException("탈퇴 기록이 없어요: " + w.id()));
    e.close(w.canceledAt(), name(w.canceledBy()), w.purgedAt(), name(w.purgedBy()));
    return toDomain(repository.saveAndFlush(e));
  }

  @Override
  public Optional<Withdrawal> findOpen(long userId) {
    return repository.findOpen(userId).map(WithdrawalJpaAdapter::toDomain);
  }

  @Override
  public List<Long> findDueUserIds(Instant now, int limit) {
    return repository.findDueUserIds(now, Limit.of(limit));
  }

  @Override
  public boolean claimPurge(long userId, Instant dueBy, Instant now, Closer by) {
    int n =
        dueBy == null
            ? repository.claimAny(userId, now, by.name())
            : repository.claimDue(userId, dueBy, now, by.name());
    return n > 0;
  }

  private static String name(Closer c) {
    return c == null ? null : c.name();
  }

  private static Closer closer(String raw) {
    return raw == null ? null : Closer.valueOf(raw);
  }

  static Withdrawal toDomain(WithdrawalEntity e) {
    return new Withdrawal(
        e.getId(),
        e.getUserId(),
        WithdrawalReason.valueOf(e.getReason()),
        e.getReasonText(),
        e.getRequestedAt(),
        e.getPurgeAfter(),
        e.getCanceledAt(),
        closer(e.getCanceledBy()),
        e.getPurgedAt(),
        closer(e.getPurgedBy()));
  }
}
