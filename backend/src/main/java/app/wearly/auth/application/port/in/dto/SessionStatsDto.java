package app.wearly.auth.application.port.in.dto;

/** 살아 있는 세션 수, 기준 시각 이후 활동한 회원 수 (관리자 대시보드) */
public record SessionStatsDto(long activeSessions, long activeUsers) {}
