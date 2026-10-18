package app.wearly.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.sharedkernel.code.DressCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 프로필 기본값·저장 전 정리 (Spring 없이) */
class UserProfileTest {

  @Test
  void 기본값은_앱_DEFAULT_PROFILE과_같다() {
    UserProfile d = UserProfile.defaults();
    assertThat(d.dressCodes()).containsExactly(DressCode.BIZ_CASUAL);
    assertThat(d.workDays()).containsExactly(1, 2, 3, 4, 5);
    assertThat(d.onboarded()).isFalse();
    assertThat(d.notifications().toMap()).containsOnlyKeys("morning", "evening", "dMinus1");
    // 나의 정보는 모두 비어 있다 (선택 입력)
    assertThat(d.personal().isEmpty()).isTrue();
  }

  @Test
  void 저장_전에_중복을_없애고_근무_요일을_정렬한다() {
    UserProfile p =
        new UserProfile(
                List.of(DressCode.FORMAL, DressCode.FORMAL),
                List.of("NEAT", "NEAT"),
                List.of("navy", "white", "navy"),
                WorkType.CUSTOM,
                List.of(5, 1, 5),
                true,
                false,
                new UserProfile.Notify(true, false, true),
                true,
                new UserProfile.PersonalInfo("AGE_30_EARLY", "WAVE", 162, "SLIM"))
            .normalized();
    assertThat(p.dressCodes()).containsExactly(DressCode.FORMAL);
    assertThat(p.colors()).containsExactly("navy", "white");
    assertThat(p.workDays()).containsExactly(1, 5);
    // 나의 정보는 그대로 둔다
    assertThat(p.personal())
        .isEqualTo(new UserProfile.PersonalInfo("AGE_30_EARLY", "WAVE", 162, "SLIM"));
    assertThat(UserProfile.Notify.fromMap(null))
        .isEqualTo(new UserProfile.Notify(false, false, false));
  }

  @Test
  void 나의_정보가_null이면_빈_값으로_둔다() {
    UserProfile p =
        new UserProfile(
            List.of(DressCode.CASUAL),
            List.of(),
            List.of(),
            WorkType.WEEKDAY,
            List.of(1),
            true,
            true,
            new UserProfile.Notify(true, true, true),
            true,
            null);
    assertThat(p.personal()).isSameAs(UserProfile.PersonalInfo.EMPTY);
    assertThat(new UserProfile.PersonalInfo(null, null, 170, null).isEmpty()).isFalse();
  }
}
