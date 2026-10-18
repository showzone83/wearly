package app.wearly.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SSO 공급자 규칙 (22 문서 4.2): 돌아갈 주소 허용 목록, 그룹·이메일 → 관리자, 이메일 검증, 설정 점검 */
class SsoProviderTest {

  static SsoProvider provider(List<String> groups, List<String> emails, boolean assumeVerified) {
    return new SsoProvider(
        "keycloak",
        "회사 계정",
        "http://localhost:8180/realms/wearly",
        null,
        "wearly-admin",
        "secret",
        List.of("profile", "email"),
        null,
        groups,
        emails,
        List.of("http://localhost:5173/sso/callback", "HTTP://LOCALHOST:5173/login"),
        null,
        assumeVerified);
  }

  static SsoProvider p = provider(List.of("wearly-admins"), List.of("Boss@Corp.test"), false);

  static IdTokenClaims claims(String email, Boolean verified) {
    return new IdTokenClaims("iss", "sub", email, verified, null, List.of(), List.of());
  }

  @Test
  void 기본값을_채운다() {
    assertThat(p.scopes()).containsExactly("openid", "profile", "email");
    assertThat(p.rolesClaim()).isEqualTo("groups");
    assertThat(p.discoveryUri())
        .isEqualTo("http://localhost:8180/realms/wearly/.well-known/openid-configuration");
    assertThat(p.problems()).isEmpty();
    assertThat(p.defaultRedirect()).isEqualTo("http://localhost:5173/sso/callback");
  }

  @Test
  void 돌아갈_주소는_허용_목록과_정확히_같아야_한다() {
    assertThat(p.allowedRedirect("http://localhost:5173/sso/callback"))
        .contains("http://localhost:5173/sso/callback");
    assertThat(p.allowedRedirect("http://LOCALHOST:5173/login"))
        .contains("http://localhost:5173/login");
    assertThat(p.allowedRedirect("http://localhost:5173/sso/callback?x=1")).isEmpty();
    assertThat(p.allowedRedirect("http://localhost:5173/sso/callback#a")).isEmpty();
    assertThat(p.allowedRedirect("http://localhost:5173/sso/callback/")).isEmpty();
    assertThat(p.allowedRedirect("http://evil@localhost:5173/sso/callback")).isEmpty();
    assertThat(p.allowedRedirect("http://localhost:5174/sso/callback")).isEmpty();
    assertThat(p.allowedRedirect("https://localhost:5173/sso/callback")).isEmpty();
    assertThat(p.allowedRedirect("//localhost:5173/sso/callback")).isEmpty();
    assertThat(p.allowedRedirect("javascript:alert(1)")).isEmpty();
    assertThat(p.allowedRedirect(null)).isEmpty();
  }

  @Test
  void 기본_포트와_빈_경로를_맞춘다() {
    assertThat(SsoRedirects.normalize("https://admin.wearly.app:443"))
        .contains("https://admin.wearly.app/");
    assertThat(SsoRedirects.normalize("http://a.test:80/x")).contains("http://a.test/x");
    assertThat(SsoRedirects.withQuery("http://a.test/x", Map.of("code", "a b")))
        .isEqualTo("http://a.test/x?code=a+b");
  }

  @Test
  void 관리자_그룹이나_검증된_관리자_이메일이면_관리자다() {
    assertThat(p.grantsAdmin(List.of("wearly-admins"), null, false)).isTrue();
    assertThat(p.grantsAdmin(List.of("WEARLY-ADMINS"), null, false)).isFalse();
    assertThat(p.grantsAdmin(List.of("wearly-viewers"), "x@corp.test", true)).isFalse();
    assertThat(p.grantsAdmin(List.of(), "boss@corp.test", true)).isTrue();
    assertThat(p.grantsAdmin(List.of(), "boss@corp.test", false)).isFalse();
  }

  @Test
  void 이메일은_검증됐거나_설정이_믿을_때만_연결에_쓴다() {
    assertThat(p.emailTrusted(claims("a@corp.test", true))).isTrue();
    assertThat(p.emailTrusted(claims("a@corp.test", false))).isFalse();
    assertThat(p.emailTrusted(claims("a@corp.test", null))).isFalse();
    assertThat(p.emailTrusted(claims(null, true))).isFalse();
    SsoProvider entra = provider(List.of("x"), List.of(), true);
    assertThat(entra.emailTrusted(claims("a@corp.test", null))).isTrue();
    // 명시적으로 false면 설정과 상관없이 믿지 않는다
    assertThat(entra.emailTrusted(claims("a@corp.test", false))).isFalse();
  }

  @Test
  void 설정이_모자라면_이유를_알려_준다() {
    SsoProvider bad =
        new SsoProvider(
            "Bad_ID", null, null, null, null, null, null, null, null, null, null, null, false);
    assertThat(bad.problems()).hasSize(6);
    assertThat(bad.displayName()).isEqualTo("Bad_ID");
  }

  @Test
  void 이름은_name_이메일_앞부분_순서로_20자까지() {
    assertThat(
            new IdTokenClaims("i", "s", "kim.long@corp.test", true, null, List.of(), List.of())
                .accountName())
        .isEqualTo("kim.long");
    assertThat(
            new IdTokenClaims("i", "s", null, null, "가".repeat(30), List.of(), List.of())
                .accountName())
        .hasSize(20);
    assertThat(new IdTokenClaims("i", "s", null, null, null, List.of(), List.of()).accountName())
        .isEqualTo("관리자");
  }

  @Test
  void PKCE_challenge는_RFC_7636_예시와_같다() {
    // RFC 7636 부록 B
    assertThat(SsoSecrets.s256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    assertThat(SsoSecrets.isFormat(SsoSecrets.newValue())).isTrue();
    assertThat(SsoSecrets.isFormat("short")).isFalse();
  }
}
