package app.wearly.auth.adapter.in.web.request;

/**
 * POST /auth/refresh. 토큰이 없거나 비어 있어도 400이 아니라 401 REFRESH_INVALID로 응답한다 (05 문서 4장 "Refresh 토큰 없음").
 */
public record RefreshRequest(String refreshToken) {}
