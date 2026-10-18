package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.SsoProviderDto;
import java.util.List;

/**
 * GET /auth/sso/providers (22 문서 4.2): 관리자 로그인 화면의 회사 계정 버튼. SSO가 꺼져 있으면 enabled false, providers 빈
 * 목록
 */
public record SsoProvidersResponse(boolean enabled, List<Provider> providers) {

  public record Provider(String id, String displayName) {}

  public static SsoProvidersResponse from(List<SsoProviderDto> list) {
    return new SsoProvidersResponse(
        !list.isEmpty(), list.stream().map(p -> new Provider(p.id(), p.displayName())).toList());
  }
}
