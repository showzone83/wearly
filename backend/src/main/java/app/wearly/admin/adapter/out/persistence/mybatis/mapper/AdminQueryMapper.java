package app.wearly.admin.adapter.out.persistence.mybatis.mapper;

import app.wearly.admin.application.port.out.AdminQueryPort.AiLogFilter;
import app.wearly.admin.application.port.out.AdminQueryPort.UserFilter;
import app.wearly.admin.application.port.out.AdminRows.AiCounts;
import app.wearly.admin.application.port.out.AdminRows.AiLogRow;
import app.wearly.admin.application.port.out.AdminRows.AuditRow;
import app.wearly.admin.application.port.out.AdminRows.Counts;
import app.wearly.admin.application.port.out.AdminRows.ProviderStat;
import app.wearly.admin.application.port.out.AdminRows.UserRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 읽기 전용 조회·집계 (MyBatis). SQL은 resources/mapper/admin/AdminQueryMapper.xml */
@Mapper
public interface AdminQueryMapper {

  Counts counts(@Param("since") Instant since);

  AiCounts aiCounts(@Param("from") Instant from, @Param("to") Instant to);

  /** [from, to) 공급자별 호출 집계 (모든 기능) */
  List<ProviderStat> providerStats(@Param("from") Instant from, @Param("to") Instant to);

  List<UserRow> findUsers(
      @Param("f") UserFilter filter,
      @Param("todayFrom") Instant todayFrom,
      @Param("todayTo") Instant todayTo,
      @Param("limit") int limit,
      @Param("offset") long offset);

  long countUsers(@Param("f") UserFilter filter);

  List<AiLogRow> findAiLogs(
      @Param("f") AiLogFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countAiLogs(@Param("f") AiLogFilter filter);

  AiLogRow findAiLog(@Param("id") long id);

  List<AuditRow> findAuditLogs(
      @Param("targetType") String targetType,
      @Param("limit") int limit,
      @Param("offset") long offset);

  long countAuditLogs(@Param("targetType") String targetType);
}
