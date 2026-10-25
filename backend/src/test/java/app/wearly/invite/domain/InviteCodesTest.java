package app.wearly.invite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InviteCodesTest {

  @Test
  void 만든_코드는_8자이고_헷갈리는_글자가_없다() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String code = InviteCodes.generate();
      assertThat(code).hasSize(8).doesNotContainPattern("[01ILO]");
      assertThat(InviteCodes.isFormat(code)).isTrue();
      seen.add(code);
    }
    assertThat(seen).hasSizeGreaterThan(990);
  }

  @Test
  void 입력은_대소문자_공백_하이픈을_무시한다() {
    assertThat(InviteCodes.normalize(" abcd-efgh ")).isEqualTo("ABCDEFGH");
    assertThat(InviteCodes.isFormat("abcd efgh")).isTrue();
    assertThat(InviteCodes.normalize(null)).isEmpty();
  }

  @Test
  void 형식이_아니면_false() {
    assertThat(InviteCodes.isFormat("ABCDEFG")).isFalse();
    assertThat(InviteCodes.isFormat("ABCDEFGHJ")).isFalse();
    assertThat(InviteCodes.isFormat("ABCD0FGH")).isFalse();
    assertThat(InviteCodes.isFormat("한글코드입니다요")).isFalse();
  }

  @Test
  void 이력에_남기는_입력은_20자까지() {
    assertThat(InviteCodes.entered("a-b-c")).isEqualTo("ABC");
    assertThat(InviteCodes.entered("x".repeat(30))).hasSize(20);
    assertThat(InviteCodes.entered("  ")).isEmpty();
  }

  @Test
  void 채널은_대소문자를_가리지_않는다() {
    assertThat(InviteChannel.parse(" sms ")).contains(InviteChannel.SMS);
    assertThat(InviteChannel.parse("KAKAO")).isEmpty();
    assertThat(InviteChannel.parse(null)).isEmpty();
  }
}
