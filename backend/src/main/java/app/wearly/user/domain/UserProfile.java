package app.wearly.user.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 취향·근무 패턴 프로필 (순수 도메인 모델). 앱 Profile과 같은 값 + onboarded (05 문서 6장). moods는 STYLE 코드, colors는 COLOR
 * 코드 (06 문서). 알림(notifications)은 앱에서 notify 필드다 (record 구성 요소 이름 notify는 Object.notify()와 겹쳐 쓸 수
 * 없다).
 */
public record UserProfile(
    List<DressCode> dressCodes,
    List<String> moods,
    List<String> colors,
    WorkType workType,
    List<Integer> workDays,
    boolean workRule,
    boolean offRule,
    Notify notifications,
    boolean onboarded) {

  public record Notify(boolean morning, boolean evening, boolean dMinus1) {

    /** 저장 모양 (JSONB, 키 순서 고정) */
    public Map<String, Boolean> toMap() {
      Map<String, Boolean> m = new LinkedHashMap<>();
      m.put("morning", morning);
      m.put("evening", evening);
      m.put("dMinus1", dMinus1);
      return m;
    }

    public static Notify fromMap(Map<String, Boolean> notify) {
      return new Notify(flag(notify, "morning"), flag(notify, "evening"), flag(notify, "dMinus1"));
    }

    private static boolean flag(Map<String, Boolean> notify, String key) {
      return notify != null && Boolean.TRUE.equals(notify.get(key));
    }
  }

  /** 앱 DEFAULT_PROFILE(mobile/src/data/seed.ts)과 같은 기본값 */
  public static UserProfile defaults() {
    return new UserProfile(
        List.of(DressCode.BIZ_CASUAL),
        List.of("NEAT", "MINIMAL"),
        List.of("navy", "beige", "white"),
        WorkType.WEEKDAY,
        List.of(1, 2, 3, 4, 5),
        true,
        true,
        new Notify(true, true, true),
        false);
  }

  /** 저장 전 정리: 중복을 없애고(순서 유지) 근무 요일은 정렬한다 */
  public UserProfile normalized() {
    return new UserProfile(
        dressCodes.stream().distinct().toList(),
        moods.stream().distinct().toList(),
        colors.stream().distinct().toList(),
        workType,
        workDays.stream().distinct().sorted().toList(),
        workRule,
        offRule,
        notifications,
        onboarded);
  }
}
