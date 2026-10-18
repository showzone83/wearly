package app.wearly.auth.application.port.in.dto;

/** TOTP 등록 시작: 비밀(base32, 수동 입력용)과 인증 앱 등록 주소(QR) */
public record MfaSetupDto(String secret, String otpauthUrl) {}
