package app.wearly.appsetting.application.port.in.dto;

/**
 * 앱이 시작할 때 읽는 공개 설정 (12 문서 3장, 이슈 #6). version은 ETag로도 쓴다.
 *
 * @param themeId 테마 ID
 * @param seasonMode 계절 테마 모드 (OFF · AUTO · SPRING · SUMMER · FALL · WINTER)
 * @param version 마지막 변경 시각 또는 "initial"
 */
public record AppConfigDto(String themeId, String seasonMode, String version) {}
