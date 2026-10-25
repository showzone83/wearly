package app.wearly.invite.application.service;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.invite.application.port.in.InviteUseCase;
import app.wearly.invite.application.port.in.dto.InviteDto;
import app.wearly.invite.application.port.in.dto.InvitedFriendDto;
import app.wearly.invite.application.port.out.InviteCodePort;
import app.wearly.invite.application.port.out.InviteHistoryPort;
import app.wearly.invite.application.port.out.InviteQueryPort;
import app.wearly.invite.application.port.out.InviteSharePolicyPort;
import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteCodes;
import app.wearly.invite.domain.InviteSharePolicy;
import app.wearly.invite.domain.InviteSharePolicy.Gate;
import app.wearly.invite.domain.InviteSignupResult;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 친구 초대 (doc/25): 코드는 JPA, 친구 목록·수는 MyBatis */
@Service
public class InviteService implements InviteUseCase {

  /** 코드가 다른 회원과 겹칠 때 다시 만드는 횟수 (31^8가지라 겹칠 일은 거의 없다) */
  static final int MAX_ATTEMPTS = 5;

  static final int FRIENDS_LIMIT = 100;

  private static final Logger log = LoggerFactory.getLogger(InviteService.class);

  private final InviteCodePort codes;
  private final InviteHistoryPort history;
  private final InviteQueryPort queries;
  private final InviteSharePolicyPort sharePolicy;
  private final Clock clock;

  public InviteService(
      InviteCodePort codes,
      InviteHistoryPort history,
      InviteQueryPort queries,
      InviteSharePolicyPort sharePolicy,
      Clock clock) {
    this.codes = codes;
    this.history = history;
    this.queries = queries;
    this.sharePolicy = sharePolicy;
    this.clock = clock;
  }

  @Override
  @Transactional
  public InviteDto myInvite(long userId) {
    return new InviteDto(codeOf(userId), queries.countInvited(userId));
  }

  private String codeOf(long userId) {
    Optional<String> found = codes.findCode(userId);
    if (found.isPresent()) {
      return found.get();
    }
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      codes.insertIfAbsent(userId, InviteCodes.generate(), clock.instant());
      // 넣지 못했어도 같은 회원이 동시에 열어 이미 생긴 경우면 그 코드를 쓴다
      found = codes.findCode(userId);
      if (found.isPresent()) {
        return found.get();
      }
    }
    throw new IllegalStateException("초대 코드를 만들지 못했어요: user " + userId);
  }

  @Override
  @Transactional
  public void recordShare(long userId, String channel) {
    InviteChannel ch =
        InviteChannel.parse(channel)
            .orElseThrow(
                () ->
                    BusinessException.validation(
                        FieldError.notAllowed("channel", InviteChannel.names())));
    // 연속 클릭·스크립트로 이력이 쌓이지 않게 (doc/25 6장). 걸려도 요청은 성공으로 끝낸다
    Gate gate =
        sharePolicy.tryAcquire(
            userId,
            ch,
            Zones.today(clock),
            InviteSharePolicy.COOLDOWN,
            InviteSharePolicy.DAILY_LIMIT);
    switch (gate) {
      case RECORD -> history.recordShare(userId, ch, clock.instant());
      case DAILY_LIMIT_FIRST ->
          log.warn(
              "초대 기록 하루 한도({}건)에 걸렸어요: user {} — 이후 오늘 기록은 남기지 않아요",
              InviteSharePolicy.DAILY_LIMIT,
              userId);
      default -> log.debug("초대 기록 건너뜀 ({}): user {} {}", gate, userId, ch);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<InvitedFriendDto> invitedFriends(long userId) {
    return queries.findInvitedFriends(userId, FRIENDS_LIMIT).stream()
        .map(r -> new InvitedFriendDto(r.name(), r.joinedAt()))
        .toList();
  }

  @Override
  @Transactional
  public void recordSignup(long newUserId, String enteredCode) {
    String entered = InviteCodes.entered(enteredCode);
    if (entered.isEmpty()) {
      return;
    }
    Optional<Long> referrer =
        InviteCodes.isFormat(entered)
            ? codes.findUserId(entered).filter(id -> id != newUserId)
            : Optional.empty();
    InviteSignupResult result =
        referrer.isPresent() ? InviteSignupResult.LINKED : InviteSignupResult.UNKNOWN_CODE;
    history.recordSignup(newUserId, referrer.orElse(null), entered, result, clock.instant());
  }
}
