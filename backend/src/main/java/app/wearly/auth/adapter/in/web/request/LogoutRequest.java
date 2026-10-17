package app.wearly.auth.adapter.in.web.request;

/** POST /auth/logout. 토큰이 없거나 틀려도 204라서 검증하지 않는다. */
public record LogoutRequest(String refreshToken) {}
