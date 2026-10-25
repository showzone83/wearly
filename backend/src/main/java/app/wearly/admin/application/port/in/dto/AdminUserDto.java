package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 회원 목록의 한 줄 (유스케이스 출력). itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷 (13 문서 3장). aiToday =
 * 오늘(한국 시간) 성공한 사진 분석 수 (한도에 세는 것과 같음). ageRange·bodyType·fitPreference(코드)·heightCm = 프로필 나의 정보
 * (조회만, 없으면 null). mfaEnrolled = TOTP 2단계 인증을 등록했는지. referrer* = 이 회원을 초대한 추천인 (없으면 null),
 * invitedCount = 이 회원의 초대 코드로 가입한 친구 수 (doc/25)
 */
public record AdminUserDto(
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
    long invitedCount) {}
