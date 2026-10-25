package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 초대 코드 가입 이력 한 행 (가입자·추천인 이메일·이름 포함, 추천인이 없으면 null) */
public record InviteSignupRowDto(
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
