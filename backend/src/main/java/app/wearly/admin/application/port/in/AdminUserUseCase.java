package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminViews.AdminUserView;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.common.domain.PatchBody;

/** 회원 목록·정지·역할 변경 (07 문서 4장 "회원") */
public interface AdminUserUseCase {

  /** q: 이메일·이름 부분 일치 (대소문자 무시) */
  PageDto<AdminUserView> list(String q, String status, String role, PageDto.Paging paging);

  /**
   * { status?, role? }. 자기 자신의 역할을 내리거나 자기 자신을 정지할 수는 없다 (400). 정지하면 그 회원의 Refresh 토큰을 모두 폐기한다 (남은
   * Access 토큰은 매 요청 회원 상태 확인으로 401).
   */
  AdminUserView patch(long adminId, long userId, PatchBody body);
}
