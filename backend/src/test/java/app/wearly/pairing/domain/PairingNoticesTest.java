package app.wearly.pairing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PairingNoticesTest {

  @Test
  void 묶음_알림은_있는_종류만_이어_붙인다() {
    assertThat(PairingNotices.digest("민지", 3, 1, 0, 0).body())
        .isEqualTo("민지님이 옷 3벌을 등록하고 1벌을 수정했어요");
    assertThat(PairingNotices.digest("민지", 0, 2, 0, 0).body()).isEqualTo("민지님이 옷 2벌을 수정했어요");
    assertThat(PairingNotices.digest("민지", 1, 1, 1, 1).body())
        .isEqualTo("민지님이 옷 1벌을 등록하고 1벌을 수정하고 1벌을 사용 중지하고 1벌을 다시 사용했어요");
    assertThat(PairingNotices.digest("민지", 0, 0, 4, 0).body()).isEqualTo("민지님이 옷 4벌을 사용 중지했어요");
  }

  @Test
  void 보낼_작업이_없으면_null() {
    assertThat(PairingNotices.digest("민지", 0, 0, 0, 0)).isNull();
  }

  @Test
  void 종류와_눌렀을_때_값() {
    PairingNotices n = PairingNotices.requested("민지");
    assertThat(n.type()).isEqualTo("closet-pairing-request");
    assertThat(n.title()).isEqualTo("옷장 도우미");
    assertThat(n.data(7L))
        .containsEntry("type", "closet-pairing-request")
        .containsEntry("pairingId", "7");
    assertThat(PairingNotices.CHANNEL_ID).isEqualTo("closet-helper");
  }
}
