package app.wearly.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void 저장_전에_중복을_없애고_근무_요일을_정렬한다() {
        UserProfile p = new UserProfile(List.of(DressCode.FORMAL, DressCode.FORMAL), List.of("NEAT", "NEAT"),
                List.of("navy", "white", "navy"), WorkType.CUSTOM, List.of(5, 1, 5), true, false,
                new UserProfile.Notify(true, false, true), true).normalized();
        assertThat(p.dressCodes()).containsExactly(DressCode.FORMAL);
        assertThat(p.colors()).containsExactly("navy", "white");
        assertThat(p.workDays()).containsExactly(1, 5);
        assertThat(UserProfile.Notify.fromMap(null)).isEqualTo(new UserProfile.Notify(false, false, false));
    }
}
