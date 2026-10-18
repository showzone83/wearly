package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * 관리자 회원 목록 항목 (07 문서 4장). itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷 (13 문서 3장). aiToday =
 * 오늘(한국 시간) 성공한 사진 분석 수
 */
public record AdminUserResponse(
    String id,
    String email,
    String name,
    String role,
    String status,
    String createdAt,
    long itemCount,
    long retiredItemCount,
    long aiToday,
    boolean mfaEnrolled) {

  public static AdminUserResponse from(AdminUserDto d) {
    return new AdminUserResponse(
        Ids.format(d.id()),
        d.email(),
        d.name(),
        d.role(),
        d.status(),
        Times.iso(d.createdAt()),
        d.itemCount(),
        d.retiredItemCount(),
        d.aiToday(),
        d.mfaEnrolled());
  }
}
