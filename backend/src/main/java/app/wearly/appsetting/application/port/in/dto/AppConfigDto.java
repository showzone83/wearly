package app.wearly.appsetting.application.port.in.dto;

/** 앱이 시작할 때 읽는 공개 설정 (12 문서 3장). version은 ETag로도 쓴다 */
public record AppConfigDto(String themeId, String version) {}
