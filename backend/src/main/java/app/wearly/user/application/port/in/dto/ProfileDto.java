package app.wearly.user.application.port.in.dto;

import app.wearly.user.domain.DressCode;
import app.wearly.user.domain.UserProfile;
import app.wearly.user.domain.WorkType;
import java.util.List;

/**
 * 프로필 (유스케이스 출력): 회원 이름 + 취향·근무 패턴 + onboarded. moods는 STYLE 코드, colors는 COLOR 코드 (06 문서). 앱
 * Profile 모양은 웹 어댑터가 만든다.
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
    boolean onboarded) {

  /** dayBefore = 앱 notify.dMinus1 (예정일 하루 전 알림) */
  public record NotifyDto(boolean morning, boolean evening, boolean dayBefore) {}

  public static ProfileDto of(String name, UserProfile p) {
    UserProfile.Notify n = p.notifications();
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
        p.onboarded());
  }
}
