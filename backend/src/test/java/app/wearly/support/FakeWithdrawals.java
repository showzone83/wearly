package app.wearly.support;

import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import app.wearly.user.application.port.in.dto.WithdrawDto;
import app.wearly.user.application.port.in.dto.WithdrawalDto;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 서비스 단위 테스트용 탈퇴 유예 입력 포트 (메모리, doc/27). 회원 상태는 FakeUsers에서 바꾼다 */
public class FakeWithdrawals implements WithdrawalUseCase {

  public static final Instant REQUESTED = Instant.parse("2026-10-25T00:00:00Z");
  public static final Instant PURGE = REQUESTED.plus(Duration.ofDays(30));

  private final FakeUsers users;
  public final Map<Long, WithdrawalDto> open = new LinkedHashMap<>();

  /** 취소한 회원과 관리자 취소 여부 */
  public final Map<Long, Boolean> canceled = new LinkedHashMap<>();

  public FakeWithdrawals(FakeUsers users) {
    this.users = users;
  }

  @Override
  public WithdrawalDto withdraw(long userId, WithdrawDto input) {
    UserAccountDto u = users.get(userId);
    users.rows.put(
        userId,
        new UserAccountDto(
            u.id(), u.email(), u.name(), u.role(), UserAccountDto.WITHDRAWN, u.createdAt()));
    WithdrawalDto w = new WithdrawalDto(userId, input.reason(), REQUESTED, PURGE);
    open.put(userId, w);
    return w;
  }

  @Override
  public Optional<WithdrawalDto> findOpen(long userId) {
    return Optional.ofNullable(open.get(userId));
  }

  @Override
  public boolean cancel(long userId, boolean byAdmin) {
    UserAccountDto u = users.get(userId);
    if (!u.isWithdrawn()) {
      return false;
    }
    users.rows.put(
        userId,
        new UserAccountDto(
            u.id(), u.email(), u.name(), u.role(), UserAccountDto.ACTIVE, u.createdAt()));
    open.remove(userId);
    canceled.put(userId, byAdmin);
    return true;
  }
}
