package app.wearly.admin.application.service;

import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.dto.AiCountsDto;
import app.wearly.admin.application.port.out.dto.AiLogRowDto;
import app.wearly.admin.application.port.out.dto.AuditLogRowDto;
import app.wearly.admin.application.port.out.dto.AuthEventLogRowDto;
import app.wearly.admin.application.port.out.dto.AuthEventRowDto;
import app.wearly.admin.application.port.out.dto.DashboardCountsDto;
import app.wearly.admin.application.port.out.dto.ProviderStatDto;
import app.wearly.admin.application.port.out.dto.UserBriefRowDto;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 서비스 단위 테스트용 AdminQueryPort. 테스트가 쓰는 메서드만 덮어쓴다 */
class UnsupportedAdminQueries implements AdminQueryPort {

  @Override
  public DashboardCountsDto counts(Instant since) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AiCountsDto aiCounts(Instant from, Instant to) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ProviderStatDto> providerStats(Instant from, Instant to) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<UserListRowDto> findUsers(
      UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countUsers(UserFilter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<AiLogRowDto> findAiLogs(AiLogFilter filter, int limit, long offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countAiLogs(AiLogFilter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AiLogRowDto findAiLog(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<AuditLogRowDto> findAuditLogs(String targetType, int limit, long offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countAuditLogs(String targetType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<UserBriefRowDto> findUserBriefs(Collection<Long> ids) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<AuthEventRowDto> findAuthEvents(long userId, int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<AuthEventLogRowDto> findAdminAuthEvents(
      AuthEventFilter filter, int limit, long offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countAdminAuthEvents(AuthEventFilter filter) {
    throw new UnsupportedOperationException();
  }
}
