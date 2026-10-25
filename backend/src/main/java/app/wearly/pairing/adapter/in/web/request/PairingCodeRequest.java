package app.wearly.pairing.adapter.in.web.request;

/**
 * POST /pairings/codes 본문. 검증은 유스케이스가 한다.
 *
 * @param role 코드를 만드는 자리: OWNER(내 옷장을 맡길 사람을 찾는다) · HELPER(옷장을 맡아 줄 사람)
 */
public record PairingCodeRequest(String role) {}
