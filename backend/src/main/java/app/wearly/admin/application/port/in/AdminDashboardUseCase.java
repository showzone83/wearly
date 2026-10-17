package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AiLogDetailDto;
import app.wearly.admin.application.port.in.dto.AiLogDto;
import app.wearly.admin.application.port.in.dto.AiLogSearchDto;
import app.wearly.admin.application.port.in.dto.AuditLogDto;
import app.wearly.admin.application.port.in.dto.DashboardDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;

/** 대시보드·AI 분석 기록·변경 이력 조회 (읽기 전용) */
public interface AdminDashboardUseCase {

  /** newUsers7d·aiByProvider7d는 지금부터 7일 전까지, aiToday는 오늘(한국 시간) */
  DashboardDto dashboard();

  /**
   * from·to: YYYY-MM-DD(한국 시간, 둘 다 그날 포함) 또는 ISO-8601 시각(from 포함, to 미포함). userEmail은 부분 일치(대소문자
   * 무시).
   */
  PageDto<AiLogDto> aiLogs(AiLogSearchDto search, PagingDto paging);

  AiLogDetailDto aiLog(long id);

  PageDto<AuditLogDto> auditLogs(String targetType, PagingDto paging);
}
