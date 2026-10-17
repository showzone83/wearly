package app.wearly.admin.adapter.out.query;

import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminRows.AiCounts;
import app.wearly.admin.application.port.out.AdminRows.AiLogRow;
import app.wearly.admin.application.port.out.AdminRows.AuditRow;
import app.wearly.admin.application.port.out.AdminRows.Counts;
import app.wearly.admin.application.port.out.AdminRows.ProviderStat;
import app.wearly.admin.application.port.out.AdminRows.UserRow;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** AdminQueryPort 구현 (MyBatis) */
@Component
class AdminQueryAdapter implements AdminQueryPort {

    private final AdminQueryMapper mapper;

    AdminQueryAdapter(AdminQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Counts counts(Instant since) {
        return mapper.counts(since);
    }

    @Override
    public AiCounts aiCounts(Instant from, Instant to) {
        return mapper.aiCounts(from, to);
    }

    @Override
    public List<ProviderStat> providerStats(Instant from, Instant to) {
        return mapper.providerStats(from, to);
    }

    @Override
    public List<UserRow> findUsers(UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset) {
        return mapper.findUsers(filter, todayFrom, todayTo, limit, offset);
    }

    @Override
    public long countUsers(UserFilter filter) {
        return mapper.countUsers(filter);
    }

    @Override
    public List<AiLogRow> findAiLogs(AiLogFilter filter, int limit, long offset) {
        return mapper.findAiLogs(filter, limit, offset);
    }

    @Override
    public long countAiLogs(AiLogFilter filter) {
        return mapper.countAiLogs(filter);
    }

    @Override
    public AiLogRow findAiLog(long id) {
        return mapper.findAiLog(id);
    }

    @Override
    public List<AuditRow> findAuditLogs(String targetType, int limit, long offset) {
        return mapper.findAuditLogs(targetType, limit, offset);
    }

    @Override
    public long countAuditLogs(String targetType) {
        return mapper.countAuditLogs(targetType);
    }
}
