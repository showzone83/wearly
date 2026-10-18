package app.wearly.auth.adapter.in.web.request;

/** POST /auth/sso/exchange: 콜백이 관리자 사이트에 붙여 준 한 번만 쓰는 교환 코드 */
public record SsoExchangeRequest(String code) {

  @Override
  public String toString() {
    return "SsoExchangeRequest[code=***]";
  }
}
