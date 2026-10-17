package app.wearly.admin.application.port.in.dto;

import java.util.List;

/** 회원 하나의 살아 있는 세션(lastSeenAt 최신순)과 최근 로그인 이력(최신순, 최대 20개) */
public record AdminUserSessionsDto(List<AdminSessionDto> sessions, List<LoginEventDto> events) {}
