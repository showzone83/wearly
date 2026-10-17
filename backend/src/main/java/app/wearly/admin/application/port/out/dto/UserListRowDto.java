package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 관리자 회원 목록 한 행. itemCount = 사용 중인 옷, retiredItemCount = 사용 중지한 옷, aiToday = 그날 성공한 사진 분석 수 */
public record UserListRowDto(
    Long id,
    String email,
    String name,
    String role,
    String status,
    Instant createdAt,
    long itemCount,
    long retiredItemCount,
    long aiToday) {}
