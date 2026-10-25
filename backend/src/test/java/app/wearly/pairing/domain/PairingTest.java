package app.wearly.pairing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PairingTest {

  static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");
  static final Duration PENDING_TTL = Duration.ofDays(7);

  @Test
  void 권한은_누적_단계다() {
    assertThat(PairingPermission.EDIT.allows(PairingPermission.VIEW)).isTrue();
    assertThat(PairingPermission.EDIT.allows(PairingPermission.EDIT)).isTrue();
    assertThat(PairingPermission.VIEW.allows(PairingPermission.EDIT)).isFalse();
  }

  @Test
  void 권한과_자리는_대소문자를_가리지_않는다() {
    assertThat(PairingPermission.parse(" edit ")).contains(PairingPermission.EDIT);
    assertThat(PairingPermission.parse("PLAN")).isEmpty();
    assertThat(PairingRole.parse("helper")).contains(PairingRole.HELPER);
    assertThat(PairingRole.parse(null)).isEmpty();
  }

  @Test
  void 주인_코드로_만든_연결은_승인을_기다리고_돕는_사람_코드면_바로_연결된다() {
    Pairing requested = Pairing.requested(1L, 2L, NOW);
    assertThat(requested.status()).isEqualTo(PairingStatus.PENDING);
    assertThat(requested.permission()).isNull();
    assertThat(requested.issuedBy()).isEqualTo(PairingRole.OWNER);

    Pairing accepted = Pairing.accepted(1L, 2L, PairingPermission.VIEW, NOW);
    assertThat(accepted.status()).isEqualTo(PairingStatus.ACTIVE);
    assertThat(accepted.approvedAt()).isEqualTo(NOW);
    assertThat(accepted.issuedBy()).isEqualTo(PairingRole.HELPER);
  }

  @Test
  void 대기는_정한_시간이_지나면_만료된다() {
    Pairing p = Pairing.requested(1L, 2L, NOW);
    assertThat(p.isOpen(NOW.plus(PENDING_TTL).minusSeconds(1), PENDING_TTL)).isTrue();
    assertThat(p.isPendingExpired(NOW.plus(PENDING_TTL), PENDING_TTL)).isTrue();
    assertThat(p.isOpen(NOW.plus(PENDING_TTL), PENDING_TTL)).isFalse();

    Pairing active = p.approve(PairingPermission.EDIT, NOW.plusSeconds(60));
    assertThat(active.isOpen(NOW.plus(Duration.ofDays(365)), PENDING_TTL)).isTrue();
  }

  @Test
  void 자리와_상대() {
    Pairing p = Pairing.requested(1L, 2L, NOW);
    assertThat(p.roleOf(1L)).isEqualTo(PairingRole.OWNER);
    assertThat(p.roleOf(2L)).isEqualTo(PairingRole.HELPER);
    assertThat(p.roleOf(3L)).isNull();
    assertThat(p.partnerOf(1L)).isEqualTo(2L);
    assertThat(p.partnerOf(2L)).isEqualTo(1L);
  }

  @Test
  void 끝나면_ENDED와_끝낸_쪽이_남는다() {
    Pairing ended =
        Pairing.accepted(1L, 2L, PairingPermission.EDIT, NOW).end(PairingEndReason.HELPER, NOW);
    assertThat(ended.status()).isEqualTo(PairingStatus.ENDED);
    assertThat(ended.endedBy()).isEqualTo(PairingEndReason.HELPER);
    assertThat(ended.endedAt()).isEqualTo(NOW);
    assertThat(ended.isOpen(NOW, PENDING_TTL)).isFalse();
  }

  @Test
  void 코드는_8자_초대_코드와_같은_글자_규칙() {
    String code = PairingCode.generate();
    assertThat(code).hasSize(8).doesNotContainPattern("[01ILO]");
    assertThat(PairingCode.isFormat(code.toLowerCase())).isTrue();
    assertThat(PairingCode.normalize(" abcd-efgh ")).isEqualTo("ABCDEFGH");
    assertThat(PairingCode.isFormat("ABCD0FGH")).isFalse();
  }
}
