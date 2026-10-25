package app.wearly.pairing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.pairing.application.port.in.dto.ClosetAccessDto;
import app.wearly.pairing.application.port.in.dto.ProxyAction;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.application.port.out.ProxyActionPort;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingEndReason;
import app.wearly.pairing.domain.PairingPermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ClosetAccessServiceTest {

  static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");
  static final long OWNER = 1L;
  static final long HELPER = 2L;

  /** 두 사람 사이의 끝나지 않은 연결 (없으면 null) */
  Pairing unended;

  final List<String> recorded = new ArrayList<>();

  final PairingPort pairings =
      new PairingPort() {
        @Override
        public Optional<Pairing> find(long pairingId) {
          return Optional.ofNullable(unended);
        }

        @Override
        public Optional<Pairing> findUnended(long ownerId, long helperId) {
          return Optional.ofNullable(unended)
              .filter(p -> p.ownerId() == ownerId && p.helperId() == helperId);
        }

        @Override
        public Pairing save(Pairing pairing) {
          throw new UnsupportedOperationException();
        }
      };

  final ProxyActionPort actions =
      (pairingId, ownerId, helperId, action, itemId, itemName, now) ->
          recorded.add(
              pairingId + ":" + ownerId + ":" + helperId + ":" + action + ":" + itemId + ":"
                  + itemName);

  final ClosetAccessService service =
      new ClosetAccessService(pairings, actions, Clock.fixed(NOW, ZoneOffset.UTC));

  static Pairing withId(Pairing p) {
    return new Pairing(
        10L,
        p.ownerId(),
        p.helperId(),
        p.permission(),
        p.status(),
        p.issuedBy(),
        p.notifyOwner(),
        p.createdAt(),
        p.approvedAt(),
        p.endedAt(),
        p.endedBy());
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void 연결이_없거나_대기_중이면_404() {
    assertError(() -> service.requireView(HELPER, OWNER), ErrorCode.NOT_FOUND);
    unended = withId(Pairing.requested(OWNER, HELPER, NOW));
    assertError(() -> service.requireView(HELPER, OWNER), ErrorCode.NOT_FOUND);
    // 방향이 반대면 다른 연결이다
    unended = withId(Pairing.accepted(OWNER, HELPER, PairingPermission.EDIT, NOW));
    assertError(() -> service.requireView(OWNER, HELPER), ErrorCode.NOT_FOUND);
  }

  @Test
  void 보기_권한은_보기만_되고_등록은_403() {
    unended = withId(Pairing.accepted(OWNER, HELPER, PairingPermission.VIEW, NOW));
    assertThat(service.requireView(HELPER, OWNER))
        .isEqualTo(new ClosetAccessDto(10L, OWNER, HELPER));
    assertError(() -> service.requireEdit(HELPER, OWNER), ErrorCode.FORBIDDEN);
  }

  @Test
  void 등록_권한은_둘_다_된다() {
    unended = withId(Pairing.accepted(OWNER, HELPER, PairingPermission.EDIT, NOW));
    assertThat(service.requireEdit(HELPER, OWNER).pairingId()).isEqualTo(10L);
    assertThat(service.requireView(HELPER, OWNER).ownerId()).isEqualTo(OWNER);
  }

  @Test
  void 끝난_연결은_404() {
    unended =
        withId(
            Pairing.accepted(OWNER, HELPER, PairingPermission.EDIT, NOW)
                .end(PairingEndReason.OWNER, NOW));
    // findUnended는 끝난 연결을 주지 않지만, 상태 확인도 한 번 더 한다
    assertError(() -> service.requireEdit(HELPER, OWNER), ErrorCode.NOT_FOUND);
  }

  @Test
  void 기록은_이름을_40자까지_남긴다() {
    ClosetAccessDto a = new ClosetAccessDto(10L, OWNER, HELPER);
    service.record(a, ProxyAction.ITEM_CREATE, 5L, "셔츠");
    service.record(a, ProxyAction.ITEM_UPDATE, 5L, "가".repeat(50));
    assertThat(recorded.get(0)).isEqualTo("10:1:2:ITEM_CREATE:5:셔츠");
    assertThat(recorded.get(1)).endsWith(":ITEM_UPDATE:5:" + "가".repeat(40));
  }
}
