package app.wearly.admin.adapter.out.persistence.mybatis;

import app.wearly.admin.adapter.out.persistence.mybatis.mapper.AdminQueryMapper;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.dto.AiCountsDto;
import app.wearly.admin.application.port.out.dto.AiLogRowDto;
import app.wearly.admin.application.port.out.dto.AuditLogRowDto;
import app.wearly.admin.application.port.out.dto.AuthEventRowDto;
import app.wearly.admin.application.port.out.dto.DashboardCountsDto;
import app.wearly.admin.application.port.out.dto.ProviderStatDto;
import app.wearly.admin.application.port.out.dto.UserBriefRowDto;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** AdminQueryPort 구현 (MyBatis) */
@Component
class AdminQueryMybatisAdapter implements AdminQueryPort {

  private final AdminQueryMapper mapper;

  AdminQueryMybatisAdapter(AdminQueryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public DashboardCountsDto counts(Instant since) {
    return mapper.counts(since);
  }

  @Override
  public AiCountsDto aiCounts(Instant from, Instant to) {
    return mapper.aiCounts(from, to);
  }

  @Override
  public List<ProviderStatDto> providerStats(Instant from, Instant to) {
    return mapper.providerStats(from, to);
  }

  @Override
  public List<UserListRowDto> findUsers(
      UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset) {
    return mapper.findUsers(filter, todayFrom, todayTo, limit, offset);
  }

  @Override
  public long countUsers(UserFilter filter) {
    return mapper.countUsers(filter);
  }

  @Override
  public List<AiLogRowDto> findAiLogs(AiLogFilter filter, int limit, long offset) {
    return mapper.findAiLogs(filter, limit, offset);
  }

  @Override
  public long countAiLogs(AiLogFilter filter) {
    return mapper.countAiLogs(filter);
  }

  @Override
  public AiLogRowDto findAiLog(long id) {
    return mapper.findAiLog(id);
  }

  @Override
  public List<AuditLogRowDto> findAuditLogs(String targetType, int limit, long offset) {
    return mapper.findAuditLogs(targetType, limit, offset);
  }

  @Override
  public long countAuditLogs(String targetType) {
    return mapper.countAuditLogs(targetType);
  }

  @Override
  public List<UserBriefRowDto> findUserBriefs(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return mapper.findUserBriefs(List.copyOf(ids));
  }

  @Override
  public List<AuthEventRowDto> findAuthEvents(long userId, int limit) {
    return mapper.findAuthEvents(userId, limit);
  }
}
