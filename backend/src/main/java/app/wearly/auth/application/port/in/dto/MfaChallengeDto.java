package app.wearly.auth.application.port.in.dto;

/**
 * 비밀번호는 맞았고 2단계 인증을 기다리는 관리자 로그인 (22 문서). mfaToken은 MFA 대기 토큰 원문(5분, 관리자 API에 쓸 수 없음)
 *
 * @param enrolled TOTP를 이미 등록했는지 (false면 등록부터)
 */
public record MfaChallengeDto(String mfaToken, long expiresInSeconds, boolean enrolled) {}
