package app.wearly.user.adapter.in.web.response;

import app.wearly.user.application.port.in.dto.ProfileDto;
import app.wearly.user.domain.DressCode;
import app.wearly.user.domain.WorkType;
import java.util.List;

/**
 * GET·PUT /users/me/profile 응답: 앱 Profile과 같은 모양 + onboarded (05 문서 6장). 필드 이름 notify가
 * Object.notify()와 겹쳐 record로 만들 수 없어 일반 클래스(읽기 전용)로 둔다.
 */
public final class ProfileResponse {

  private final String name;
  private final List<DressCode> dressCodes;
  private final List<String> moods;
  private final List<String> colors;
  private final WorkType workType;
  private final List<Integer> workDays;
  private final boolean workRule;
  private final boolean offRule;
  private final Notify notify;
  private final boolean onboarded;

  private ProfileResponse(ProfileDto p) {
    this.name = p.name();
    this.dressCodes = p.dressCodes();
    this.moods = p.moods();
    this.colors = p.colors();
    this.workType = p.workType();
    this.workDays = p.workDays();
    this.workRule = p.workRule();
    this.offRule = p.offRule();
    ProfileDto.NotifyDto n = p.notifications();
    this.notify = new Notify(n.morning(), n.evening(), n.dayBefore());
    this.onboarded = p.onboarded();
  }

  public static ProfileResponse from(ProfileDto profile) {
    return new ProfileResponse(profile);
  }

  /** dMinus1은 앱과 맞춘 JSON 필드 이름이라 그대로 쓴다 (Checkstyle 이름 규칙 예외) */
  @SuppressWarnings("checkstyle:RecordComponentName")
  public record Notify(boolean morning, boolean evening, boolean dMinus1) {}

  public String getName() {
    return name;
  }

  public List<DressCode> getDressCodes() {
    return dressCodes;
  }

  public List<String> getMoods() {
    return moods;
  }

  public List<String> getColors() {
    return colors;
  }

  public WorkType getWorkType() {
    return workType;
  }

  public List<Integer> getWorkDays() {
    return workDays;
  }

  public boolean getWorkRule() {
    return workRule;
  }

  public boolean getOffRule() {
    return offRule;
  }

  public Notify getNotify() {
    return notify;
  }

  public boolean getOnboarded() {
    return onboarded;
  }
}
