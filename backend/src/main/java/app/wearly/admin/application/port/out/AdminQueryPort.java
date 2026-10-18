package app.wearly.admin.application.port.out;

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

  /** 회원 요약 (로그인 세션 목록용). 없는(탈퇴한) 회원은 빠진다. ids가 비면 빈 목록 */
  List<UserBriefRowDto> findUserBriefs(Collection<Long> ids);

  /** 그 회원의 로그인 이력 최신순 (tb_auth_event) */
  List<AuthEventRowDto> findAuthEvents(long userId, int limit);

  /**
   * 관리자 로그인 기록 (22 문서): 지금 관리자인 회원의 이벤트 + 관리자 사이트(client ADMIN)에서 일어난 이벤트, 최신순. from·to는 [from, to)
   */
  List<AuthEventLogRowDto> findAdminAuthEvents(AuthEventFilter filter, int limit, long offset);

  long countAdminAuthEvents(AuthEventFilter filter);

  record AuthEventFilter(Long userId, String type, Instant from, Instant to) {}

  /** id가 있으면 그 회원 하나만 */
  record UserFilter(Long id, String query, String status, String role) {}

  record AiLogFilter(
      String providerId, String status, String email, String feature, Instant from, Instant to) {}
}
