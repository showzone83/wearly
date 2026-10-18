package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;

/** 회원 목록·정지·역할 지정·해제·2단계 인증 초기화 (07 문서 4장 "회원", 22 문서 4.1) */
public interface AdminUserUseCase {

  /** q: 이메일·이름 부분 일치 (대소문자 무시) */
  PageDto<AdminUserDto> list(String q, String status, String role, PagingDto paging);

  /**
   * { status?, role? }. 자기 자신의 역할을 내리거나 자기 자신을 정지할 수는 없다 (400 SELF_NOT_ALLOWED). 정지된 회원은 관리자로 지정할 수
   * 없다 (400 role CONFLICT). 쓸 수 있는 관리자(ADMIN·ACTIVE)가 한 명도 남지 않게 되면 409 LAST_ADMIN. 정지·관리자 해제는 그
   * 회원의 로그인 세션을 모두 끊는다 (16 문서, FORCED_LOGOUT 기록. 남은 Access 토큰은 다음 요청부터 401 TOKEN_INVALID). 역할이 바뀌면
   * 변경 이력 USER_ROLE_CHANGE, 그 밖은 USER_UPDATE.
   */
  AdminUserDto patch(long adminId, long userId, PatchUserDto patch);

  /**
   * 다른 회원의 2단계 인증(TOTP·복구 코드) 초기화. 그 회원의 세션을 모두 끊고 변경 이력 USER_MFA_RESET. 자기 자신은 400(id
   * SELF_NOT_ALLOWED), 없는 회원 404. 등록하지 않았어도 204
   */
  void resetMfa(long adminId, long userId);
}
