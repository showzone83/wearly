package app.wearly.invite.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.invite.application.port.out.InviteCodePort;
import app.wearly.invite.application.port.out.InviteHistoryPort;
import app.wearly.invite.application.port.out.InviteQueryPort;
import app.wearly.invite.application.port.out.InviteSharePolicyPort;
import app.wearly.invite.application.port.out.dto.InvitedFriendRowDto;
import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSharePolicy.Gate;
import app.wearly.invite.domain.InviteSignupResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InviteServiceTest {

  static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");

  /** 회원 → 코드 */
  final Map<Long, String> codeRows = new HashMap<>();

  /** 다음 넣기를 몇 번 실패시킬지 (다른 회원과 코드가 겹친 경우) */
  int collisions;

  final List<String> shares = new ArrayList<>();
  final List<String> signups = new ArrayList<>();

  final InviteCodePort codes =
      new InviteCodePort() {
        @Override
        public Optional<String> findCode(long userId) {
          return Optional.ofNullable(codeRows.get(userId));
        }

        @Override
        public Optional<Long> findUserId(String code) {
          return codeRows.entrySet().stream()
              .filter(e -> e.getValue().equals(code))
              .map(Map.Entry::getKey)
              .findFirst();
        }

        @Override
        public boolean insertIfAbsent(long userId, String code, Instant now) {
          if (collisions > 0) {
            collisions--;
            return false;
          }
          return codeRows.putIfAbsent(userId, code) == null;
        }
      };

  final InviteHistoryPort history =
      new InviteHistoryPort() {
        @Override
        public void recordShare(long userId, InviteChannel channel, Instant now) {
          shares.add(userId + ":" + channel);
        }

        @Override
        public void recordSignup(
            long userId,
            Long referrerId,
            String enteredCode,
            InviteSignupResult result,
            Instant now) {
          signups.add(userId + ":" + referrerId + ":" + enteredCode + ":" + result);
        }
      };

  final InviteQueryPort queries =
      new InviteQueryPort() {
        @Override
        public long countInvited(long referrerId) {
          return signups.stream().filter(s -> s.contains(":" + referrerId + ":")).count();
        }

        @Override
        public List<InvitedFriendRowDto> findInvitedFriends(long referrerId, int limit) {
          return List.of();
        }
      };

  /** 다음 한도 확인 결과 (기본: 기록) */
  Gate gate = Gate.RECORD;

  final List<String> gateCalls = new ArrayList<>();

  final InviteSharePolicyPort policy =
      (userId, channel, day, cooldown, dailyLimit) -> {
        gateCalls.add(userId + ":" + channel + ":" + day + ":" + cooldown + ":" + dailyLimit);
        return gate;
      };

  final InviteService service =
      new InviteService(codes, history, queries, policy, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 처음_열면_코드를_만들고_다시_열면_같은_코드() {
    String first = service.myInvite(1L).code();
    assertThat(first).hasSize(8);
    assertThat(service.myInvite(1L).code()).isEqualTo(first);
    assertThat(codeRows).hasSize(1);
  }

  @Test
  void 코드가_겹치면_다시_만든다() {
    collisions = 2;
    assertThat(service.myInvite(1L).code()).hasSize(8);
  }

  @Test
  void 계속_겹치면_오류() {
    collisions = InviteService.MAX_ATTEMPTS;
    assertThatThrownBy(() -> service.myInvite(1L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 맞는_코드로_가입하면_LINKED_없는_코드면_UNKNOWN_CODE() {
    codeRows.put(1L, "ABCDEFGH");

    service.recordSignup(2L, "abcd-efgh");
    service.recordSignup(3L, "ZZZZZZZZ");
    service.recordSignup(4L, "아무거나");
    service.recordSignup(5L, "  ");
    service.recordSignup(6L, null);

    assertThat(signups)
        .containsExactly(
            "2:1:ABCDEFGH:LINKED", "3:null:ZZZZZZZZ:UNKNOWN_CODE", "4:null:아무거나:UNKNOWN_CODE");
    assertThat(service.myInvite(1L).invitedCount()).isEqualTo(1);
  }

  @Test
  void 공유_기록은_정한_채널만() {
    service.recordShare(1L, "sms");
    assertThat(shares).containsExactly("1:SMS");

    assertThatThrownBy(() -> service.recordShare(1L, "KAKAO"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    // 잘못된 채널은 한도를 세지 않는다. 날짜는 한국 날짜(NOW = 2026-10-25 12:00 KST)
    assertThat(gateCalls).containsExactly("1:SMS:2026-10-25:PT1M:30");
  }

  @Test
  void 쿨다운이나_하루_한도에_걸리면_기록하지_않고_성공으로_끝난다() {
    for (Gate g : List.of(Gate.COOLDOWN, Gate.DAILY_LIMIT_FIRST, Gate.DAILY_LIMIT)) {
      gate = g;
      service.recordShare(1L, "COPY");
    }
    assertThat(shares).isEmpty();
    assertThat(gateCalls).hasSize(3);
  }
}
