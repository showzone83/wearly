package app.wearly.auth.application.port.out;

/** Access 토큰(JWT HS256) 발급. sub = userId, iss = wearly, role = USER | ADMIN */
public interface AccessTokenIssuer {

    /** role 클레임은 화면 표시용이다. 서버 권한 판단은 매 요청 DB의 역할로 한다 */
    String issue(long userId, String role);

    long accessTtlSeconds();
}
