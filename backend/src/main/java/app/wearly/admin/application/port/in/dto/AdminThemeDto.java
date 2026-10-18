package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 테마 화면 (12 문서 3장, 유스케이스 출력). 바꾼 적이 없으면 updatedAt·updatedBy는 null, updatedBy는 바꾼 관리자 이메일(탈퇴하면
 * null). seasonMode는 계절 테마 모드 이름(이슈 #6)
 */
public record AdminThemeDto(
    String themeId, String seasonMode, Instant updatedAt, String updatedBy) {}
