package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 회원 목록의 한 줄 (유스케이스 출력). itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷 (13 문서 3장). aiToday =
 * 오늘(한국 시간) 성공한 사진 분석 수 (한도에 세는 것과 같음)
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
    boolean mfaEnrolled) {}
