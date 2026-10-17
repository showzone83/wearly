package app.wearly.admin.application.port.in.dto;

import java.util.List;

/**
 * 관리자 대시보드 (유스케이스 출력). newUsers7d·aiByProvider7d는 지금부터 7일 전까지, aiToday는 오늘(한국 시간).
 *
 * @param aiByProvider7d 공급자별 호출 집계 (모든 기능)
 */
public record DashboardDto(
    long users,
    long newUsers7d,
    long items,
    AiTodayDto aiToday,
    List<ProviderUsageDto> aiByProvider7d) {

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
