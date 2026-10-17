package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;

/** 회원 목록·정지·역할 변경 (07 문서 4장 "회원") */
public interface AdminUserUseCase {

  /** q: 이메일·이름 부분 일치 (대소문자 무시) */
  PageDto<AdminUserDto> list(String q, String status, String role, PagingDto paging);

  /**
   * { status?, role? }. 자기 자신의 역할을 내리거나 자기 자신을 정지할 수는 없다 (400). 정지하면 그 회원의 로그인 세션을 모두 끊는다 (16 문서,
   * FORCED_LOGOUT 기록. 남은 Access 토큰은 다음 요청부터 401 TOKEN_INVALID).
   */
  AdminUserDto patch(long adminId, long userId, PatchUserDto patch);
}
