package app.wearly.admin.adapter.out.persistence.mybatis.mapper;

import app.wearly.admin.application.port.out.AdminQueryPort.AiLogFilter;
import app.wearly.admin.application.port.out.AdminQueryPort.AuthEventFilter;
import app.wearly.admin.application.port.out.AdminQueryPort.UserFilter;
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
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 읽기 전용 조회·집계 (MyBatis). SQL은 resources/mapper/admin/AdminQueryMapper.xml */
@Mapper
public interface AdminQueryMapper {

  DashboardCountsDto counts(@Param("since") Instant since);

  AiCountsDto aiCounts(@Param("from") Instant from, @Param("to") Instant to);

  /** [from, to) 공급자별 호출 집계 (모든 기능) */
  List<ProviderStatDto> providerStats(@Param("from") Instant from, @Param("to") Instant to);

  List<UserListRowDto> findUsers(
      @Param("f") UserFilter filter,
      @Param("todayFrom") Instant todayFrom,
      @Param("todayTo") Instant todayTo,
      @Param("limit") int limit,
      @Param("offset") long offset);

  long countUsers(@Param("f") UserFilter filter);

  List<AiLogRowDto> findAiLogs(
      @Param("f") AiLogFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countAiLogs(@Param("f") AiLogFilter filter);

  AiLogRowDto findAiLog(@Param("id") long id);

  List<AuditLogRowDto> findAuditLogs(
      @Param("targetType") String targetType,
      @Param("limit") int limit,
      @Param("offset") long offset);

  long countAuditLogs(@Param("targetType") String targetType);

  List<UserBriefRowDto> findUserBriefs(@Param("ids") List<Long> ids);

  List<AuthEventRowDto> findAuthEvents(@Param("userId") long userId, @Param("limit") int limit);

  List<AuthEventLogRowDto> findAdminAuthEvents(
      @Param("f") AuthEventFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countAdminAuthEvents(@Param("f") AuthEventFilter filter);
}
