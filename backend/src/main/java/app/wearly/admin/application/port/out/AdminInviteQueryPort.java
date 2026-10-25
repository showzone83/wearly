package app.wearly.admin.application.port.out;

import app.wearly.admin.application.port.out.dto.InviteShareRowDto;
import app.wearly.admin.application.port.out.dto.InviteSignupRowDto;
import java.util.List;

/** 친구 초대 이력 읽기 (MyBatis, tb_invite_signup · tb_invite_share와 회원) */
public interface AdminInviteQueryPort {

  List<InviteSignupRowDto> findSignups(SignupFilter filter, int limit, long offset);

  long countSignups(SignupFilter filter);

  List<InviteShareRowDto> findShares(ShareFilter filter, int limit, long offset);

  long countShares(ShareFilter filter);

  /** query: 가입자·추천인 이메일·이름, 입력 코드 부분 일치. result: LINKED · UNKNOWN_CODE (null이면 모두) */
  record SignupFilter(String query, String result) {}

  /** query: 회원 이메일·이름 부분 일치. channel: SHARE · SMS · COPY (null이면 모두) */
  record ShareFilter(String query, String channel) {}
}
