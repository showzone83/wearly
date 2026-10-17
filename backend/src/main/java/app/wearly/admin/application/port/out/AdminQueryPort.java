package app.wearly.admin.application.port.out;

import app.wearly.admin.application.port.out.AdminRows.AiCounts;
import app.wearly.admin.application.port.out.AdminRows.AiLogRow;
import app.wearly.admin.application.port.out.AdminRows.AuditRow;
import app.wearly.admin.application.port.out.AdminRows.Counts;
import app.wearly.admin.application.port.out.AdminRows.ProviderStat;
import app.wearly.admin.application.port.out.AdminRows.UserRow;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 읽기 전용 조회·집계 (MyBatis). 대시보드·목록용 읽기 모델이라 여러 테이블(회원·옷장·AI 기록)을 함께 읽는다. 쓰기는 각 컨텍스트의 입력 포트로만 한다.
 */
public interface AdminQueryPort {

  Counts counts(Instant since);

  AiCounts aiCounts(Instant from, Instant to);

  /** [from, to) 공급자별 호출 집계 (모든 기능) */
  List<ProviderStat> providerStats(Instant from, Instant to);

  List<UserRow> findUsers(
      UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset);

  long countUsers(UserFilter filter);

  List<AiLogRow> findAiLogs(AiLogFilter filter, int limit, long offset);

  long countAiLogs(AiLogFilter filter);

  AiLogRow findAiLog(long id);

  List<AuditRow> findAuditLogs(String targetType, int limit, long offset);

  long countAuditLogs(String targetType);

  /** id가 있으면 그 회원 하나만 */
  record UserFilter(Long id, String queryPattern, String status, String role) {}

  record AiLogFilter(
      String providerId,
      String status,
      String emailPattern,
      String feature,
      Instant from,
      Instant to) {}
}
