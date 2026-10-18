package app.wearly.auth.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 2단계 인증 상태
 *
 * @param enrolledAt 등록하지 않았으면 null
 * @param required 서버 설정 wearly.admin.mfa.required
 */
public record MfaStatusDto(
    boolean enrolled, Instant enrolledAt, int recoveryCodesLeft, boolean required) {}
