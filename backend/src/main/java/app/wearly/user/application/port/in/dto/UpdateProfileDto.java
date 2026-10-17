package app.wearly.user.application.port.in.dto;

import app.wearly.sharedkernel.code.DressCode;
import app.wearly.user.application.port.in.dto.ProfileDto.NotifyDto;
import app.wearly.user.domain.UserProfile;
import app.wearly.user.domain.WorkType;
import java.util.List;

/** 프로필 바꾸기 (PUT, 전체 교체) 입력. 회원 이름도 함께 바꾼다 */
public record UpdateProfileDto(
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

  public UserProfile toProfile() {
    return new UserProfile(
        dressCodes,
        moods,
        colors,
        workType,
        workDays,
        workRule,
        offRule,
        new UserProfile.Notify(
            notifications.morning(), notifications.evening(), notifications.dayBefore()),
        onboarded);
  }
}
