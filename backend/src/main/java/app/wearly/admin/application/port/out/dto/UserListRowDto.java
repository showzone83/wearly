package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/**
 * 관리자 회원 목록 한 행. itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷, aiToday = 그날 성공한 사진 분석 수.
 * ageRange·bodyType·heightCm·fitPreference = 프로필 나의 정보 (없으면 null). mfaEnrolled = TOTP 등록 여부.
 * referrerId·referrerEmail·referrerName = 이 회원을 초대한 추천인 (초대 코드 가입 LINKED, 없으면 null). invitedCount =
 * 이 회원의 코드로 가입한 친구 수 (doc/25). purgeAt = 탈퇴 예정이면 삭제 예정 시각 (doc/27, 아니면 null)
 */
public record UserListRowDto(
    Long id,
    String email,
    String name,
    String role,
    String status,
    Instant createdAt,
    long itemCount,
    long retiredItemCount,
    long aiToday,
    String ageRange,
    String bodyType,
    Integer heightCm,
    String fitPreference,
    boolean mfaEnrolled,
    Long referrerId,
    String referrerEmail,
    String referrerName,
    long invitedCount,
    Instant purgeAt) {}
