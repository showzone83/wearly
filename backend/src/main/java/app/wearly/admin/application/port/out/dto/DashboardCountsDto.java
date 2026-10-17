package app.wearly.admin.application.port.out.dto;

/** 대시보드 개수: 전체 회원, 기준 시각 뒤 새 회원, 전체 아이템 */
public record DashboardCountsDto(long users, long newUsers7d, long items) {}
