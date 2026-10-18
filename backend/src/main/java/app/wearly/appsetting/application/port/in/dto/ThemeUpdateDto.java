package app.wearly.appsetting.application.port.in.dto;

/**
 * 테마 설정 바꾸기 입력 (유스케이스 입력). 보낸 값만 바꾸고 null은 지금 값을 그대로 둔다. 둘 다 null이면 400 VALIDATION_FAILED(themeId)
 *
 * @param themeId 테마 ID (nyang · closeta)
 * @param seasonMode 계절 테마 모드 (OFF · AUTO · SPRING · SUMMER · FALL · WINTER)
 */
public record ThemeUpdateDto(String themeId, String seasonMode) {}
