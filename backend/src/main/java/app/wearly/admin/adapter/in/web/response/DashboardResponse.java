package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.DashboardDto;
import java.util.List;

/** GET /admin/dashboard (07 문서 4장). activeSessions·activeUsers24h는 16 문서 4장 */
public record DashboardResponse(
    long users,
    long newUsers7d,
    long items,
    AiToday aiToday,
    List<ProviderStat> aiByProvider7d,
    long activeSessions,
    long activeUsers24h) {

  public record AiToday(long total, long success, long failed) {}

  public record ProviderStat(
      String providerId,
      long success,
      long failed,
      Long avgLatencyMs,
      long inputTokens,
      long outputTokens) {}

  public static DashboardResponse from(DashboardDto d) {
    return new DashboardResponse(
        d.users(),
        d.newUsers7d(),
        d.items(),
        new AiToday(d.aiToday().total(), d.aiToday().success(), d.aiToday().failed()),
        d.aiByProvider7d().stream()
            .map(
                p ->
                    new ProviderStat(
                        p.providerId(),
                        p.success(),
                        p.failed(),
                        p.avgLatencyMs(),
                        p.inputTokens(),
                        p.outputTokens()))
            .toList(),
        d.activeSessions(),
        d.activeUsers24h());
  }
}
