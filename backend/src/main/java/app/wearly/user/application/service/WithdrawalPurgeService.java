package app.wearly.user.application.service;

import app.wearly.user.application.port.in.WithdrawalPurgeUseCase;
import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.WithdrawalPurgeRunDto;
import app.wearly.user.application.port.out.WithdrawalPort;
import app.wearly.user.domain.WithdrawalPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 예약 삭제 (doc/27 2단계): 기한이 지난 회원을 하나씩 지운다. 회원마다 {@link WithdrawalUseCase#purgeIfDue}(별도 트랜잭션)를 불러
 * 한 명이 실패해도 나머지는 계속한다. 로그에는 회원 ID와 건수만 남긴다
 */
@Service
public class WithdrawalPurgeService implements WithdrawalPurgeUseCase {

  private static final Logger log = LoggerFactory.getLogger(WithdrawalPurgeService.class);

  private final WithdrawalPort withdrawals;
  private final WithdrawalUseCase purger;
  private final WithdrawalPolicy policy;
  private final Clock clock;

  public WithdrawalPurgeService(
      WithdrawalPort withdrawals, WithdrawalUseCase purger, WithdrawalPolicy policy, Clock clock) {
    this.withdrawals = withdrawals;
    this.purger = purger;
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  public WithdrawalPurgeRunDto purgeDue() {
    return purgeDue(clock.instant());
  }

  @Override
  public WithdrawalPurgeRunDto purgeDue(Instant now) {
    List<Long> due = withdrawals.findDueUserIds(now, policy.purgeBatch());
    int purged = 0;
    int skipped = 0;
    int failed = 0;
    for (long userId : due) {
      try {
        if (purger.purgeIfDue(userId, now)) {
          purged++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("탈퇴 예약 삭제 실패 (다음에 다시): user {}", userId, e);
      }
    }
    if (!due.isEmpty()) {
      log.info(
          "탈퇴 예약 삭제 ({} 기준): 대상 {}, 삭제 {}, 건너뜀 {}, 실패 {}",
          now,
          due.size(),
          purged,
          skipped,
          failed);
    }
    return new WithdrawalPurgeRunDto(due.size(), purged, skipped, failed);
  }
}
