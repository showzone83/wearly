package app.wearly.auth.application.port.in.dto;

/** POST /auth/mfa/verify: code(TOTP 6자리)나 recoveryCode 중 하나 */
public record MfaVerifyDto(String mfaToken, String code, String recoveryCode) {}
