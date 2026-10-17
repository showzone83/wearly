package app.wearly.auth.domain;

/** Access 토큰(JWT) 클레임 이름 (05 문서 5장). 발급(출력 어댑터)과 확인(입력 어댑터)이 같이 쓴다 */
public final class AccessTokenClaims {

  /** USER | ADMIN. 화면 표시용 (서버 권한은 매 요청 DB의 역할로 정한다) */
  public static final String ROLE = "role";

  /** 로그인 세션 ID. 매 요청 세션이 살아 있는지 확인한다 (없으면 401 TOKEN_INVALID) */
  public static final String SESSION_ID = "sid";

  private AccessTokenClaims() {}
}
