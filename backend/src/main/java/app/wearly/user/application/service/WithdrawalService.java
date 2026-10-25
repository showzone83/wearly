package app.wearly.user.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.user.application.port.in.UserWithdrawnListener;
import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.WithdrawDto;
import app.wearly.user.application.port.in.dto.WithdrawalDto;
import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.application.port.out.WithdrawalPort;
import app.wearly.user.domain.User;
import app.wearly.user.domain.Withdrawal;
import app.wearly.user.domain.Withdrawal.Closer;
import app.wearly.user.domain.WithdrawalPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 유예 (doc/27 1단계): 회원 상태와 탈퇴 기록을 한 트랜잭션에서 바꾼다 */
@Service
public class WithdrawalService implements WithdrawalUseCase {

  private final UserPort users;
  private final WithdrawalPort withdrawals;
  private final List<UserWithdrawnListener> listeners;
  private final WithdrawalPolicy policy;
  private final Clock clock;

  public WithdrawalService(
      UserPort users,
      WithdrawalPort withdrawals,
      List<UserWithdrawnListener> listeners,
      WithdrawalPolicy policy,
      Clock clock) {
    this.users = users;
    this.withdrawals = withdrawals;
    this.listeners = List.copyOf(listeners);
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  @Transactional
  public WithdrawalDto withdraw(long userId, WithdrawDto input) {
    User user = users.findById(userId).orElseThrow(BusinessException::notFound);
    if (user.isWithdrawn()) {
      Optional<Withdrawal> open = withdrawals.findOpen(userId);
      if (open.isPresent()) {
        return toDto(open.get());
      }
    }
    Instant now = clock.instant();
    // 입력이 틀리면 상태를 바꾸기 전에 400
    Withdrawal requested =
        Withdrawal.request(userId, input.reason(), input.reasonText(), now, policy.grace());
    user.withdraw();
    users.save(user);
    Withdrawal saved = withdrawals.save(requested);
    listeners.forEach(l -> l.userWithdrawn(userId));
    return toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WithdrawalDto> findOpen(long userId) {
    return withdrawals.findOpen(userId).map(WithdrawalService::toDto);
  }

  @Override
  @Transactional
  public boolean cancel(long userId, boolean byAdmin) {
    User user = users.findById(userId).orElse(null);
    if (user == null || !user.cancelWithdrawal()) {
      return false;
    }
    users.save(user);
    withdrawals
        .findOpen(userId)
        .ifPresent(
            w -> withdrawals.save(w.cancel(byAdmin ? Closer.ADMIN : Closer.USER, clock.instant())));
    return true;
  }

  private static WithdrawalDto toDto(Withdrawal w) {
    return new WithdrawalDto(w.userId(), w.reason().name(), w.requestedAt(), w.purgeAfter());
  }
}
