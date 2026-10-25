package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.InviteShareLogDto;
import app.wearly.admin.application.port.in.dto.InviteSignupLogDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;

/** 친구 초대 이력 (doc/25, 07 문서 4장 "친구 초대"). 읽기만 한다 */
public interface AdminInviteUseCase {

  /**
   * 초대 코드 가입 이력, 최신순. q: 가입자·추천인 이메일·이름, 입력 코드 부분 일치 (대소문자 무시). result: LINKED · UNKNOWN_CODE.
   *
   * <p>오류: 400 VALIDATION_FAILED (result NOT_ALLOWED, page·size)
   */
  PageDto<InviteSignupLogDto> signups(String q, String result, PagingDto paging);

  /**
   * 초대한 이력, 최신순. q: 회원 이메일·이름 부분 일치. channel: SHARE · SMS · COPY.
   *
   * <p>오류: 400 VALIDATION_FAILED (channel NOT_ALLOWED, page·size)
   */
  PageDto<InviteShareLogDto> shares(String q, String channel, PagingDto paging);
}
