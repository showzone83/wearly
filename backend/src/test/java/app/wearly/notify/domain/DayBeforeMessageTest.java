package app.wearly.notify.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DayBeforeMessageTest {

  static DayBeforeWeather w(double temp, double rain, String source) {
    return new DayBeforeWeather(temp, rain, source);
  }

  @Test
  void 기온_3도와_비_여부로_예보가_바뀌었는지_본다() {
    DayBeforeWeather saved = w(15, 20, "CLIMATE");
    assertThat(w(17.4, 20, "FORECAST").changedFrom(saved)).isFalse(); // 반올림 17 → 2도
    assertThat(w(17.5, 20, "FORECAST").changedFrom(saved)).isTrue(); // 반올림 18 → 3도
    assertThat(w(12, 49, "FORECAST").changedFrom(saved)).isTrue(); // -3도
    assertThat(w(15, 50, "FORECAST").changedFrom(saved)).isTrue(); // 비 여부 바뀜 (50 이상)
    assertThat(w(15, 49.4, "FORECAST").changedFrom(saved)).isFalse();
    assertThat(w(15, 0, "FORECAST").changedFrom(w(15, 60, "FORECAST"))).isTrue();
  }

  @Test
  void 보통_문구() {
    DayBeforeMessage m =
        DayBeforeMessage.of("결혼식 하객", "도쿄", w(18, 10, "FORECAST"), w(18.2, 10, "FORECAST"));
    assertThat(m.changed()).isFalse();
    assertThat(m.title()).isEqualTo("내일 예정 코디");
    assertThat(m.body()).isEqualTo("내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 예보 18°C");
  }

  @Test
  void 다시_확인하지_못하면_저장된_날씨와_출처로_보낸다() {
    DayBeforeMessage m = DayBeforeMessage.of("출근", null, w(9.6, 0, "CLIMATE"), null);
    assertThat(m.body()).isEqualTo("내일 ‘출근’ 예정이에요 · 평년 10°C");
    DayBeforeMessage manual = DayBeforeMessage.of("출근", "  부산 ", w(21, 0, "MANUAL"), null);
    assertThat(manual.body()).isEqualTo("내일 ‘출근’ 예정이에요 · 부산 · 직접 입력 21°C");
  }

  @Test
  void 예보가_바뀐_문구() {
    DayBeforeMessage m =
        DayBeforeMessage.of("결혼식 하객", "도쿄", w(14, 10, "CLIMATE"), w(18, 70, "FORECAST"));
    assertThat(m.changed()).isTrue();
    assertThat(m.title()).isEqualTo("예보가 바뀌었어요");
    assertThat(m.body())
        .isEqualTo("내일 ‘결혼식 하객’ · 도쿄 · 예보 18°C (저장할 때 14°C) · 비 소식이 있어요 · 코디를 다시 확인해 보세요");
    DayBeforeMessage dry =
        DayBeforeMessage.of("등산", null, w(10, 80, "FORECAST"), w(10, 0, "FORECAST"));
    assertThat(dry.body())
        .isEqualTo("내일 ‘등산’ · 예보 10°C (저장할 때 10°C) · 비 소식이 없어졌어요 · 코디를 다시 확인해 보세요");
  }
}
