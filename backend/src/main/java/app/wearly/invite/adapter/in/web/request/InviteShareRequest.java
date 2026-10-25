package app.wearly.invite.adapter.in.web.request;

/**
 * POST /users/me/invite/shares 본문. 검증은 유스케이스가 한다.
 *
 * @param channel SHARE(공유 시트) · SMS(문자) · COPY(코드 복사)
 */
public record InviteShareRequest(String channel) {}
