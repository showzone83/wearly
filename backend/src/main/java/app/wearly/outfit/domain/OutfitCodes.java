package app.wearly.outfit.domain;

import app.wearly.sharedkernel.code.Slots;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코디 추천 요청의 고정 값 (09 문서 3장, 앱 mobile/src/data/constants.ts와 같은 값). 상황별 기대 격식·분위기별 스타일은 앱 규칙 엔진
 * (recommend.ts)과 같다.
 */
public final class OutfitCodes {

  /** 코디 칸 (06 문서 5장, 공유 커널 Slots). 칸 순서도 이 순서 */
  public static final String TOP = Slots.TOP;

  public static final String BOTTOM = Slots.BOTTOM;
  public static final String DRESS = Slots.DRESS;
  public static final String OUTER = Slots.OUTER;
  public static final String SHOES = Slots.SHOES;
  public static final String BAG = Slots.BAG;
  public static final List<String> SLOTS = Slots.ALL;

  /** 상황: 라벨과 기대 격식(1~3) */
  public record Occasion(String label, double formality) {}

  public static final Map<String, Occasion> OCCASIONS =
      ordered(
          "COMMUTE", new Occasion("출근", 2),
          "MEETING", new Occasion("회의", 2.5),
          "FIELD", new Occasion("외근", 2),
          "DINNER", new Occasion("회식", 1.5),
          "DAILY", new Occasion("데일리", 1),
          "INTERVIEW", new Occasion("면접", 3),
          "WEDDING", new Occasion("결혼식 하객", 2.7),
          "DATE", new Occasion("데이트", 1.7),
          "TRAVEL", new Occasion("여행", 1),
          "GATHERING", new Occasion("모임", 1.5));

  /** 분위기: 라벨과 어울리는 STYLE 코드 */
  public record Mood(String label, List<String> styles) {}

  public static final Map<String, Mood> MOODS =
      ordered(
          "NEAT", new Mood("단정", List.of("NEAT", "CLASSIC", "MINIMAL")),
          "COMFY", new Mood("편안", List.of("CASUAL", "SPORTY")),
          "CHIC", new Mood("세련", List.of("MINIMAL", "CLASSIC", "FEMININE")));

  /** 격식(드레스 코드): 라벨과 단계(1~3) */
  public record Formality(String label, int level) {}

  public static final Map<String, Formality> FORMALITIES =
      ordered(
          "CASUAL", new Formality("편한 데일리", 1),
          "BIZ_CASUAL", new Formality("비즈니스 캐주얼", 2),
          "FORMAL", new Formality("포멀", 3));

  public static final Map<String, String> TIMES_OF_DAY =
      ordered("AM", "오전", "PM", "오후", "EVENING", "저녁");

  public static final Map<String, String> WEATHER_CONDITIONS =
      ordered("SUNNY", "맑음", "CLOUDY", "흐림", "RAIN", "비");

  private OutfitCodes() {}

  @SuppressWarnings("unchecked")
  private static <V> Map<String, V> ordered(Object... keyValues) {
    Map<String, V> out = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      out.put((String) keyValues[i], (V) keyValues[i + 1]);
    }
    return Collections.unmodifiableMap(out);
  }
}
