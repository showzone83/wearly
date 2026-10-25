package app.wearly.user.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.user.application.port.in.UserUseCase;
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
  private final UserUseCase accounts;
  private final List<UserWithdrawnListener> listeners;
  private final WithdrawalPolicy policy;
  private final Clock clock;

  public WithdrawalService(
      UserPort users,
      WithdrawalPort withdrawals,
      UserUseCase accounts,
      List<UserWithdrawnListener> listeners,
      WithdrawalPolicy policy,
      Clock clock) {
    this.users = users;
    this.withdrawals = withdrawals;
    this.accounts = accounts;
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

  @Override
  @Transactional
  public boolean purgeIfDue(long userId, Instant now) {
    return purge(userId, now, now, Closer.SCHEDULER);
  }

  @Override
  @Transactional
  public boolean purgeNow(long userId) {
    return purge(userId, null, clock.instant(), Closer.ADMIN);
  }

  /** 처리권을 잡고(기록에 삭제) 회원을 지운다. 회원 행·하위 데이터·세션·사진은 UserUseCase.delete가 지운다 */
  private boolean purge(long userId, Instant dueBy, Instant now, Closer by) {
    if (!withdrawals.claimPurge(userId, dueBy, now, by)) {
      return false;
    }
    User user = users.findById(userId).orElse(null);
    if (user == null) {
      return true; // 이미 지워졌다 (기록만 닫는다)
    }
    if (!user.isWithdrawn()) {
      // 기록은 열려 있는데 회원이 탈퇴 예정이 아니다: 지우지 않고 기록도 되돌린다
      throw new IllegalStateException("탈퇴 예정이 아닌 회원은 지우지 않아요: user " + userId);
    }
    accounts.delete(userId);
    return true;
  }

  private static WithdrawalDto toDto(Withdrawal w) {
    return new WithdrawalDto(w.userId(), w.reason().name(), w.requestedAt(), w.purgeAfter());
  }
}
