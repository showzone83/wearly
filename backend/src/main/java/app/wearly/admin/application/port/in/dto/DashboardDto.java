package app.wearly.admin.application.port.in.dto;

import java.util.List;

/**
 * 관리자 대시보드 (유스케이스 출력). newUsers7d·aiByProvider7d는 지금부터 7일 전까지, aiToday는 오늘(한국 시간).
 *
 * @param aiByProvider7d 공급자별 호출 집계 (모든 기능)
 * @param activeSessions 살아 있는 로그인 세션 수 (16 문서 4장)
 * @param activeUsers24h 24시간 안에 활동한(살아 있는 세션의 lastSeenAt) 회원 수
 */
public record DashboardDto(
    long users,
    long newUsers7d,
    long items,
    AiTodayDto aiToday,
    List<ProviderUsageDto> aiByProvider7d,
    long activeSessions,
    long activeUsers24h) {

  public record AiTodayDto(long total, long success, long failed) {}

  /** avgLatencyMs는 호출이 없으면 null */
  public record ProviderUsageDto(
      String providerId,
      long success,
      long failed,
      Long avgLatencyMs,
      long inputTokens,
      long outputTokens) {}
}
