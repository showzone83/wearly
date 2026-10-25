package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 초대 코드 가입 이력 한 줄 (유스케이스 출력). referrer*는 추천인 (UNKNOWN_CODE거나 추천인이 탈퇴하면 null). enteredCode = 가입자가
 * 입력한 코드 (정리한 값)
 */
public record InviteSignupLogDto(
    Long id,
    Instant createdAt,
    Long userId,
    String userEmail,
    String userName,
    String enteredCode,
    String result,
    Long referrerId,
    String referrerEmail,
    String referrerName) {}
