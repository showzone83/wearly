package app.wearly.user;

import app.wearly.domain.ColorKey;
import app.wearly.domain.DressCode;
import app.wearly.domain.StyleTag;
import app.wearly.domain.WorkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 앱 Profile과 같은 모양 + onboarded (계약 6장).
 * 필드 이름 notify가 Object.notify()와 겹쳐 record로 만들 수 없어 일반 클래스로 둔다.
 */
public class ProfileDto {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 20, message = "이름은 20자까지 쓸 수 있어요.")
    private String name;

    @NotNull(message = "필수 값이에요.")
    private List<@NotNull(message = "허용되지 않는 값이에요.") DressCode> dressCodes;

    @NotNull(message = "필수 값이에요.")
    private List<@NotNull(message = "허용되지 않는 값이에요.") StyleTag> moods;

    @NotNull(message = "필수 값이에요.")
    private List<@NotNull(message = "허용되지 않는 값이에요.") ColorKey> colors;

    @NotNull(message = "필수 값이에요.")
    private WorkType workType;

    @NotNull(message = "필수 값이에요.")
    private List<@NotNull(message = "필수 값이에요.") @Min(value = 0, message = "0(일)~6(토)이어야 해요.")
            @Max(value = 6, message = "0(일)~6(토)이어야 해요.") Integer> workDays;

    @NotNull(message = "필수 값이에요.")
    private Boolean workRule;

    @NotNull(message = "필수 값이에요.")
    private Boolean offRule;

    @NotNull(message = "필수 값이에요.")
    @Valid
    private Notify notify;

    /** PUT에서 빠지면 저장과 함께 온보딩을 마친 것으로 본다 */
    private Boolean onboarded;

    public ProfileDto() {
    }

    public ProfileDto(String name, List<DressCode> dressCodes, List<StyleTag> moods, List<ColorKey> colors,
            WorkType workType, List<Integer> workDays, Boolean workRule, Boolean offRule, Notify notify,
            Boolean onboarded) {
        this.name = name;
        this.dressCodes = dressCodes;
        this.moods = moods;
        this.colors = colors;
        this.workType = workType;
        this.workDays = workDays;
        this.workRule = workRule;
        this.offRule = offRule;
        this.notify = notify;
        this.onboarded = onboarded;
    }

    public record Notify(
            @NotNull(message = "필수 값이에요.") Boolean morning,
            @NotNull(message = "필수 값이에요.") Boolean evening,
            @NotNull(message = "필수 값이에요.") Boolean dMinus1) {
    }

    /** 앱 DEFAULT_PROFILE(mobile/src/data/seed.ts)과 같은 기본값. 이름은 회원 이름을 쓴다. */
    public static ProfileDto defaults(String name) {
        return new ProfileDto(
                name,
                List.of(DressCode.BIZ_CASUAL),
                List.of(StyleTag.NEAT, StyleTag.MINIMAL),
                List.of(ColorKey.NAVY, ColorKey.BEIGE, ColorKey.WHITE),
                WorkType.WEEKDAY,
                List.of(1, 2, 3, 4, 5),
                true,
                true,
                new Notify(true, true, true),
                false);
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

    public List<StyleTag> getMoods() {
        return moods;
    }

    public void setMoods(List<StyleTag> moods) {
        this.moods = moods;
    }

    public List<ColorKey> getColors() {
        return colors;
    }

    public void setColors(List<ColorKey> colors) {
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
