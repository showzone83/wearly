package app.wearly.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.WithdrawDto;
import app.wearly.user.application.port.in.dto.WithdrawalDto;
import app.wearly.user.application.port.in.dto.WithdrawalPurgeRunDto;
import app.wearly.user.application.port.out.WithdrawalPort;
import app.wearly.user.domain.Withdrawal;
import app.wearly.user.domain.Withdrawal.Closer;
import app.wearly.user.domain.WithdrawalPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 탈퇴 예약 삭제 반복 (doc/27 2단계): 한 회원이 실패해도 나머지는 계속하고, 건너뜀·실패를 센다 */
class WithdrawalPurgeServiceTest {

  static final Instant NOW = Instant.parse("2026-11-30T00:00:00Z");

  final List<Long> due = new ArrayList<>(List.of(1L, 2L, 3L, 4L));
  final List<Long> asked = new ArrayList<>();
  int limitSeen;

  final WithdrawalPort port =
      new WithdrawalPort() {
        @Override
        public Withdrawal save(Withdrawal withdrawal) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Withdrawal> findOpen(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findDueUserIds(Instant now, int limit) {
          limitSeen = limit;
          return due;
        }

        @Override
        public boolean claimPurge(long userId, Instant dueBy, Instant now, Closer by) {
          throw new UnsupportedOperationException();
        }
      };

  /** 1: 지움, 2: 실패, 3: 건너뜀(그새 취소), 4: 지움 */
  final WithdrawalUseCase purger =
      new WithdrawalUseCase() {
        @Override
        public WithdrawalDto withdraw(long userId, WithdrawDto input) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WithdrawalDto> findOpen(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(long userId, boolean byAdmin) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean purgeIfDue(long userId, Instant now) {
          asked.add(userId);
          assertThat(now).isEqualTo(NOW);
          if (userId == 2L) {
            throw new IllegalStateException("저장소 오류");
          }
          return userId != 3L;
        }

        @Override
        public boolean purgeNow(long userId) {
          throw new UnsupportedOperationException();
        }
      };

  final WithdrawalPurgeService service =
      new WithdrawalPurgeService(
          port,
          purger,
          new WithdrawalPolicy(Duration.ofDays(30), 50),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 한_명이_실패해도_나머지는_계속하고_결과를_센다() {
    WithdrawalPurgeRunDto run = service.purgeDue();
    assertThat(asked).containsExactly(1L, 2L, 3L, 4L);
    assertThat(run).isEqualTo(new WithdrawalPurgeRunDto(4, 2, 1, 1));
    assertThat(limitSeen).isEqualTo(50);
  }

  @Test
  void 대상이_없으면_아무것도_안_한다() {
    due.clear();
    assertThat(service.purgeDue(NOW)).isEqualTo(new WithdrawalPurgeRunDto(0, 0, 0, 0));
    assertThat(asked).isEmpty();
  }
}
