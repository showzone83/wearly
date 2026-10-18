package app.wearly.auth.application.port.in.dto;

/**
 * 매 요청 인증 결과 (16 문서 3장, 22 문서)
 *
 * @param role 지금 DB의 역할 USER | ADMIN
 * @param mfaRequired 관리자인데 이 세션이 2단계 인증을 거치지 않았고 그 관리자에게 2단계 인증이 필요함 → 관리자 API 403 MFA_REQUIRED
 */
public record AuthenticatedDto(String role, boolean mfaRequired) {}
