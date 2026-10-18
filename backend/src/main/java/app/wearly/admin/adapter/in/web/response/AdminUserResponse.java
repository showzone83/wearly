package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * 관리자 회원 목록 항목 (07 문서 4장). itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷 (13 문서 3장). aiToday =
 * 오늘(한국 시간) 성공한 사진 분석 수. ageRange(AGE_RANGE)·bodyType(BODY_TYPE)·fitPreference(FIT) 코드와 heightCm =
 * 프로필 나의 정보 (조회만, 없으면 null). mfaEnrolled = TOTP 2단계 인증을 등록했는지
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
    String ageRange,
    String bodyType,
    Integer heightCm,
    String fitPreference,
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
        d.ageRange(),
        d.bodyType(),
        d.heightCm(),
        d.fitPreference(),
        d.mfaEnrolled());
  }
}
