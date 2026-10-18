package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/**
 * 관리자 회원 목록 한 행. itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷, aiToday = 그날 성공한 사진 분석 수.
 * ageRange·bodyType·heightCm·fitPreference = 프로필 나의 정보 (없으면 null). mfaEnrolled = TOTP 등록 여부
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
    boolean mfaEnrolled) {}
