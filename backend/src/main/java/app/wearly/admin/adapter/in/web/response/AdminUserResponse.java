package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/** 관리자 회원 목록 항목 (07 문서 4장). aiToday = 오늘(한국 시간) 성공한 사진 분석 수 */
public record AdminUserResponse(
    String id,
    String email,
    String name,
    String role,
    String status,
    String createdAt,
    long itemCount,
    long aiToday) {

  public static AdminUserResponse from(AdminUserDto d) {
    return new AdminUserResponse(
        Ids.format(d.id()),
        d.email(),
        d.name(),
        d.role(),
        d.status(),
        Times.iso(d.createdAt()),
        d.itemCount(),
        d.aiToday());
  }
}
