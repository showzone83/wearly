package app.wearly.appsetting.application.port.in.dto;

/**
 * 테마 바꾸기 결과. 같은 테마로 바꾸면 changed = false이고 저장하지 않는다(before = after).
 *
 * @param before 바꾸기 전
 * @param after 바꾼 뒤 (저장된 모습)
 */
public record ThemeChangeDto(ThemeSettingDto before, ThemeSettingDto after, boolean changed) {}
