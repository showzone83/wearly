package app.wearly.admin.application.port.out;

import app.wearly.admin.application.port.out.dto.AiCountsDto;
import app.wearly.admin.application.port.out.dto.AiLogRowDto;
import app.wearly.admin.application.port.out.dto.AuditLogRowDto;
import app.wearly.admin.application.port.out.dto.DashboardCountsDto;
import app.wearly.admin.application.port.out.dto.ProviderStatDto;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 읽기 전용 조회·집계 (MyBatis). 대시보드·목록용 읽기 모델이라 여러 테이블(회원·옷장·AI 기록)을 함께 읽는다. 쓰기는 각 컨텍스트의 입력 포트로만 한다.
 */
public interface AdminQueryPort {

  DashboardCountsDto counts(Instant since);

  AiCountsDto aiCounts(Instant from, Instant to);

  /** [from, to) 공급자별 호출 집계 (모든 기능) */
  List<ProviderStatDto> providerStats(Instant from, Instant to);

  List<UserListRowDto> findUsers(
      UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset);

  long countUsers(UserFilter filter);

  List<AiLogRowDto> findAiLogs(AiLogFilter filter, int limit, long offset);

  long countAiLogs(AiLogFilter filter);

  AiLogRowDto findAiLog(long id);

  List<AuditLogRowDto> findAuditLogs(String targetType, int limit, long offset);

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
