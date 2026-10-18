package app.wearly.user.application.port.in.dto;

import app.wearly.sharedkernel.code.DressCode;
import app.wearly.user.domain.UserProfile;
import app.wearly.user.domain.WorkType;
import java.util.List;

/**
 * 프로필 (유스케이스 출력): 회원 이름 + 취향·근무 패턴 + 나의 정보 + onboarded. moods는 STYLE 코드, colors는 COLOR 코드 (06 문서).
 * 나의 정보(ageRange·bodyType·heightCm·fitPreference)는 모두 선택이라 null일 수 있다. 앱 Profile 모양은 웹 어댑터가 만든다.
 */
public record ProfileDto(
    String name,
    List<DressCode> dressCodes,
    List<String> moods,
    List<String> colors,
    WorkType workType,
    List<Integer> workDays,
    boolean workRule,
    boolean offRule,
    NotifyDto notifications,
    String ageRange,
    String bodyType,
    Integer heightCm,
    String fitPreference,
    boolean onboarded) {

  /** dayBefore = 앱 notify.dMinus1 (예정일 하루 전 알림) */
  public record NotifyDto(boolean morning, boolean evening, boolean dayBefore) {}

  public static ProfileDto of(String name, UserProfile p) {
    UserProfile.Notify n = p.notifications();
    UserProfile.PersonalInfo me = p.personal();
    return new ProfileDto(
        name,
        p.dressCodes(),
        p.moods(),
        p.colors(),
        p.workType(),
        p.workDays(),
        p.workRule(),
        p.offRule(),
        new NotifyDto(n.morning(), n.evening(), n.dayBefore()),
        me.ageRange(),
        me.bodyType(),
        me.heightCm(),
        me.fitPreference(),
        p.onboarded());
  }
}
