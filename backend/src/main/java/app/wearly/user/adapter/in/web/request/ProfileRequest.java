package app.wearly.user.adapter.in.web.request;

import app.wearly.sharedkernel.code.DressCode;
import app.wearly.user.application.port.in.dto.ProfileDto.NotifyDto;
import app.wearly.user.application.port.in.dto.UpdateProfileDto;
import app.wearly.user.domain.UserProfile;
import app.wearly.user.domain.WorkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.Range;

/**
 * PUT /users/me/profile 본문: 앱 Profile과 같은 모양 + onboarded (05 문서 6장). moods는 STYLE 코드, colors는 COLOR
 * 코드 (06 문서). 필드 이름 notify가 Object.notify()와 겹쳐 record로 만들 수 없어 일반 클래스로 둔다.
 *
 * <p>나의 정보(ageRange = AGE_RANGE 코드, bodyType = BODY_TYPE 코드, heightCm 130~210, fitPreference = FIT
 * 코드)는 모두 선택이다. 전체 교체라서 빠지거나 null이면 비운다. 코드 값은 서비스가 코드 테이블로 검증한다.
 */
public class ProfileRequest {

  @NotBlank
  @Size(max = 20)
  private String name;

  @NotNull private List<@NotNull DressCode> dressCodes;

  @NotNull private List<@NotNull String> moods;

  @NotNull private List<@NotNull String> colors;

  @NotNull private WorkType workType;

  @NotNull private List<@NotNull @Range(min = 0, max = 6) Integer> workDays;

  @NotNull private Boolean workRule;

  @NotNull private Boolean offRule;

  @NotNull @Valid private Notify notify;

  private String ageRange;

  private String bodyType;

  @Range(min = UserProfile.PersonalInfo.HEIGHT_MIN, max = UserProfile.PersonalInfo.HEIGHT_MAX)
  private Integer heightCm;

  private String fitPreference;

  /** PUT에서 빠지면 저장과 함께 온보딩을 마친 것으로 본다 */
  private Boolean onboarded;

  public ProfileRequest() {}

  /** dMinus1은 앱과 맞춘 JSON 필드 이름이라 그대로 쓴다 (Checkstyle 이름 규칙 예외) */
  @SuppressWarnings("checkstyle:RecordComponentName")
  public record Notify(
      @NotNull Boolean morning, @NotNull Boolean evening, @NotNull Boolean dMinus1) {}

  /** PUT 요청 → 유스케이스 입력. onboarded가 빠지면 저장과 함께 온보딩을 마친 것으로 본다 */
  public UpdateProfileDto toDto() {
    return new UpdateProfileDto(
        name,
        dressCodes,
        moods,
        colors,
        workType,
        workDays,
        workRule,
        offRule,
        new NotifyDto(notify.morning(), notify.evening(), notify.dMinus1()),
        ageRange,
        bodyType,
        heightCm,
        fitPreference,
        onboarded == null || onboarded);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<DressCode> getDressCodes() {
    return dressCodes;
  }

  public void setDressCodes(List<DressCode> dressCodes) {
    this.dressCodes = dressCodes;
  }

  public List<String> getMoods() {
    return moods;
  }

  public void setMoods(List<String> moods) {
    this.moods = moods;
  }

  public List<String> getColors() {
    return colors;
  }

  public void setColors(List<String> colors) {
    this.colors = colors;
  }

  public WorkType getWorkType() {
    return workType;
  }

  public void setWorkType(WorkType workType) {
    this.workType = workType;
  }

  public List<Integer> getWorkDays() {
    return workDays;
  }

  public void setWorkDays(List<Integer> workDays) {
    this.workDays = workDays;
  }

  public Boolean getWorkRule() {
    return workRule;
  }

  public void setWorkRule(Boolean workRule) {
    this.workRule = workRule;
  }

  public Boolean getOffRule() {
    return offRule;
  }

  public void setOffRule(Boolean offRule) {
    this.offRule = offRule;
  }

  public Notify getNotify() {
    return notify;
  }

  public void setNotify(Notify notify) {
    this.notify = notify;
  }

  public String getAgeRange() {
    return ageRange;
  }

  public void setAgeRange(String ageRange) {
    this.ageRange = ageRange;
  }

  public String getBodyType() {
    return bodyType;
  }

  public void setBodyType(String bodyType) {
    this.bodyType = bodyType;
  }

  public Integer getHeightCm() {
    return heightCm;
  }

  public void setHeightCm(Integer heightCm) {
    this.heightCm = heightCm;
  }

  public String getFitPreference() {
    return fitPreference;
  }

  public void setFitPreference(String fitPreference) {
    this.fitPreference = fitPreference;
  }

  public Boolean getOnboarded() {
    return onboarded;
  }

  public void setOnboarded(Boolean onboarded) {
    this.onboarded = onboarded;
  }
}
