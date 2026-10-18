package app.wearly.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 관리자 사이트 SSO 공급자 하나 (22 문서 4.2, 설정 wearly.sso.providers.&lt;id&gt;). OIDC 표준만 쓰므로 Keycloak·Entra
 * ID·Google Workspace·Okta 모두 같은 모양이다.
 *
 * @param id 설정의 공급자 ID (경로 /auth/sso/{id}/…, 영문 소문자·숫자·하이픈 1~50자)
 * @param displayName 로그인 화면 버튼 이름
 * @param issuer 발급자(iss). ID 토큰의 iss와 discovery 문서의 issuer가 이 값과 글자 그대로 같아야 한다
 * @param discoveryUri discovery 문서 주소. 비우면 issuer + /.well-known/openid-configuration
 * @param clientId IdP에 등록한 클라이언트 ID (ID 토큰 aud)
 * @param clientSecret 클라이언트 비밀 (application-local.yml·환경변수)
 * @param scopes 요청할 scope (openid는 늘 넣는다)
 * @param rolesClaim 그룹·역할 클레임 이름. 점으로 중첩 경로를 쓸 수 있다 (예: realm_access.roles)
 * @param adminGroups 이 중 하나가 클레임에 있으면 관리자 (대소문자 구분)
 * @param adminEmails 그룹 클레임이 없는 IdP(Google Workspace)용: 검증된 이메일이 이 목록에 있으면 관리자 (대소문자 무시)
 * @param redirectAllowlist 로그인 뒤·로그아웃 뒤 돌아갈 관리자 사이트 주소 (정확히 같은 주소만, 열린 리디렉트 방지)
 * @param callbackUri IdP에 등록한 백엔드 콜백 주소. 비우면 요청 주소로 만든다 (…/api/v1/auth/sso/{id}/callback)
 * @param assumeEmailVerified email_verified 클레임을 주지 않는 IdP(Entra ID)에서 이메일을 검증된 것으로 볼지 (기본 false)
 */
public record SsoProvider(
    String id,
    String displayName,
    String issuer,
    String discoveryUri,
    String clientId,
    String clientSecret,
    List<String> scopes,
    String rolesClaim,
    List<String> adminGroups,
    List<String> adminEmails,
    List<String> redirectAllowlist,
    String callbackUri,
    boolean assumeEmailVerified) {

  public static final Pattern ID_FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{0,49}$");
  public static final String DEFAULT_ROLES_CLAIM = "groups";
  public static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");

  public SsoProvider {
    displayName = blankToNull(displayName) == null ? id : displayName.strip();
    // iss는 글자 그대로 비교한다 (끝의 / 도 그대로. Auth0처럼 /로 끝나는 발급자도 있다)
    issuer = blankToNull(issuer);
    discoveryUri =
        blankToNull(discoveryUri) == null
            ? (issuer == null ? null : stripSlash(issuer) + "/.well-known/openid-configuration")
            : discoveryUri.strip();
    clientId = blankToNull(clientId);
    clientSecret = blankToNull(clientSecret);
    scopes = withOpenid(scopes == null || scopes.isEmpty() ? DEFAULT_SCOPES : scopes);
    rolesClaim = blankToNull(rolesClaim) == null ? DEFAULT_ROLES_CLAIM : rolesClaim.strip();
    adminGroups = clean(adminGroups);
    adminEmails = clean(adminEmails).stream().map(e -> e.toLowerCase(Locale.ROOT)).toList();
    redirectAllowlist =
        clean(redirectAllowlist).stream()
            .map(u -> SsoRedirects.normalize(u).orElse(null))
            .filter(u -> u != null)
            .toList();
    callbackUri = blankToNull(callbackUri);
  }

  /** 설정이 쓸 수 있는 모양인지. 문제가 있으면 그 이유들 (로그용) */
  public List<String> problems() {
    List<String> p = new java.util.ArrayList<>();
    if (id == null || !ID_FORMAT.matcher(id).matches()) {
      p.add("공급자 ID는 영문 소문자·숫자·하이픈 1~50자");
    }
    if (issuer == null) {
      p.add("issuer-uri 없음");
    }
    if (clientId == null) {
      p.add("client-id 없음");
    }
    if (clientSecret == null) {
      p.add("client-secret 없음");
    }
    if (redirectAllowlist.isEmpty()) {
      p.add("redirect-allowlist 없음 (관리자 사이트 콜백 주소)");
    }
    if (adminGroups.isEmpty() && adminEmails.isEmpty()) {
      p.add("admin-groups·admin-emails 모두 없음 (아무도 관리자가 될 수 없음)");
    }
    return p;
  }

  /** 관리자 사이트 주소가 허용 목록에 있으면 정규화한 주소 */
  public Optional<String> allowedRedirect(String url) {
    return SsoRedirects.normalize(url).filter(redirectAllowlist::contains);
  }

  /** 상태가 사라져 돌아갈 주소를 모를 때 쓰는 기본 주소 (허용 목록의 첫 주소) */
  public String defaultRedirect() {
    return redirectAllowlist.getFirst();
  }

  /**
   * IdP가 준 그룹·이메일로 관리자인지 정한다. 그룹이 admin-groups 중 하나와 같거나, 검증된 이메일이 admin-emails에 있으면 관리자다.
   *
   * @param emailVerified email_verified가 true(또는 assumeEmailVerified)인지
   */
  public boolean grantsAdmin(Collection<String> groups, String email, boolean emailVerified) {
    if (groups != null && groups.stream().anyMatch(adminGroups::contains)) {
      return true;
    }
    return emailVerified && email != null && adminEmails.contains(email.toLowerCase(Locale.ROOT));
  }

  /** 이메일을 계정 연결에 써도 되는지: email_verified가 true이거나, 설정이 그 IdP의 이메일을 믿기로 했을 때 */
  public boolean emailTrusted(IdTokenClaims claims) {
    if (claims.email() == null) {
      return false;
    }
    return Boolean.TRUE.equals(claims.emailVerified())
        || (assumeEmailVerified && claims.emailVerified() == null);
  }

  public String scopeParam() {
    return String.join(" ", scopes);
  }

  private static List<String> withOpenid(List<String> s) {
    Set<String> set =
        s.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::strip)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    if (!set.contains("openid")) {
      java.util.LinkedHashSet<String> withOpenid = new java.util.LinkedHashSet<>();
      withOpenid.add("openid");
      withOpenid.addAll(set);
      set = withOpenid;
    }
    return List.copyOf(set);
  }

  private static List<String> clean(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String stripSlash(String s) {
    if (s == null) {
      return null;
    }
    String v = s;
    while (v.endsWith("/")) {
      v = v.substring(0, v.length() - 1);
    }
    return v;
  }

  @Override
  public String toString() {
    return "SsoProvider[id=" + id + ", issuer=" + issuer + ", clientId=" + clientId + "]";
  }
}
