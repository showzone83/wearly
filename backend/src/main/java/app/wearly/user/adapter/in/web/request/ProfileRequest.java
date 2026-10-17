package app.wearly.user.adapter.in.web.request;

import app.wearly.user.application.port.in.dto.ProfileDto.NotifyDto;
import app.wearly.user.application.port.in.dto.UpdateProfileDto;
import app.wearly.user.domain.DressCode;
import app.wearly.user.domain.WorkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /users/me/profile 본문: 앱 Profile과 같은 모양 + onboarded (05 문서 6장). moods는 STYLE 코드, colors는 COLOR
 * 코드 (06 문서). 필드 이름 notify가 Object.notify()와 겹쳐 record로 만들 수 없어 일반 클래스로 둔다.
 */
public class ProfileRequest {

  @NotBlank(message = "이름을 입력해 주세요.")
  @Size(max = 20, message = "이름은 20자까지 쓸 수 있어요.")
  private String name;

  @NotNull(message = "필수 값이에요.")
  private List<@NotNull(message = "허용되지 않는 값이에요.") DressCode> dressCodes;

  @NotNull(message = "필수 값이에요.")
  private List<@NotNull(message = "허용되지 않는 값이에요.") String> moods;

  @NotNull(message = "필수 값이에요.")
  private List<@NotNull(message = "허용되지 않는 값이에요.") String> colors;

  @NotNull(message = "필수 값이에요.")
  private WorkType workType;

  @NotNull(message = "필수 값이에요.")
  private List<
          @NotNull(message = "필수 값이에요.") @Min(value = 0, message = "0(일)~6(토)이어야 해요.")
          @Max(value = 6, message = "0(일)~6(토)이어야 해요.") Integer>
      workDays;

  @NotNull(message = "필수 값이에요.")
  private Boolean workRule;

  @NotNull(message = "필수 값이에요.")
  private Boolean offRule;

  @NotNull(message = "필수 값이에요.")
  @Valid
  private Notify notify;

  /** PUT에서 빠지면 저장과 함께 온보딩을 마친 것으로 본다 */
  private Boolean onboarded;

  public ProfileRequest() {}

  /** dMinus1은 앱과 맞춘 JSON 필드 이름이라 그대로 쓴다 (Checkstyle 이름 규칙 예외) */
  @SuppressWarnings("checkstyle:RecordComponentName")
  public record Notify(
      @NotNull(message = "필수 값이에요.") Boolean morning,
      @NotNull(message = "필수 값이에요.") Boolean evening,
      @NotNull(message = "필수 값이에요.") Boolean dMinus1) {}

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

  public Boolean getOnboarded() {
    return onboarded;
  }

  public void setOnboarded(Boolean onboarded) {
    this.onboarded = onboarded;
  }
}
