package app.wearly.notify.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * D-1 알림 문구 (앱 lib/planNotifyRules.ts의 로컬 알림 문구와 같은 모양).
 *
 * <ul>
 *   <li>보통: 제목 "내일 예정 코디", 본문 "내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 예보 18°C"
 *   <li>예보가 바뀜: 제목 "예보가 바뀌었어요", 본문 "내일 ‘결혼식 하객’ · 도쿄 · 예보 18°C (저장할 때 14°C) · 코디를 다시 확인해 보세요"
 * </ul>
 *
 * 장소가 없으면(앱의 "내 지역") 장소 칸을 뺀다. 비 여부가 바뀌었으면 "비 소식이 있어요" · "비 소식이 없어졌어요"를 기온 뒤에 붙인다.
 */
public record DayBeforeMessage(String title, String body, boolean changed) {

  public static final String TITLE = "내일 예정 코디";
  public static final String CHANGED_TITLE = "예보가 바뀌었어요";

  /**
   * @param placeLabel 예정 장소 이름. 없으면 null
   * @param saved 저장된 날씨
   * @param fresh 다시 확인한 날씨. 확인하지 못했으면 null (저장된 날씨로 보낸다)
   */
  public static DayBeforeMessage of(
      String planTitle, String placeLabel, DayBeforeWeather saved, DayBeforeWeather fresh) {
    DayBeforeWeather shown = fresh != null ? fresh : saved;
    boolean changed = fresh != null && fresh.changedFrom(saved);
    String weather = shown.sourceLabel() + " " + shown.roundedTemp() + "°C";
    List<String> parts = new ArrayList<>();
    parts.add(changed ? "내일 ‘" + planTitle + "’" : "내일 ‘" + planTitle + "’ 예정이에요");
    if (placeLabel != null && !placeLabel.isBlank()) {
      parts.add(placeLabel.strip());
    }
    if (!changed) {
      parts.add(weather);
      return new DayBeforeMessage(TITLE, String.join(" · ", parts), false);
    }
    parts.add(weather + " (저장할 때 " + saved.roundedTemp() + "°C)");
    if (fresh.rainy() != saved.rainy()) {
      parts.add(fresh.rainy() ? "비 소식이 있어요" : "비 소식이 없어졌어요");
    }
    parts.add("코디를 다시 확인해 보세요");
    return new DayBeforeMessage(CHANGED_TITLE, String.join(" · ", parts), true);
  }
}
