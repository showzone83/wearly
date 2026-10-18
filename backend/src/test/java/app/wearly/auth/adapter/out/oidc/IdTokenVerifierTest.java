package app.wearly.auth.adapter.out.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.OidcRejectedException;
import app.wearly.auth.domain.OidcRejectedException.Reason;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ID 토큰 확인 (OIDC Core 3.1.3.7): 테스트용 RSA 키로 직접 서명해 서명·iss·aud·exp·iat·nonce 실패를 본다 */
class IdTokenVerifierTest {

  static final String ISS = "http://localhost:8180/realms/wearly";
  static final String CLIENT = "wearly-admin";
  static final String NONCE = "n-0123456789";
  static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

  static RSAKey key;
  static RSAKey otherKey;
  static JWKSource<SecurityContext> jwks;

  @BeforeAll
  static void keys() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("k1").generate();
    otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    jwks = new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()));
  }

  static JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer(ISS)
        .subject("sub-1")
        .audience(CLIENT)
        .issueTime(Date.from(NOW.minusSeconds(5)))
        .expirationTime(Date.from(NOW.plusSeconds(300)))
        .claim("azp", CLIENT)
        .claim("nonce", NONCE)
        .claim("email", "Admin@Corp.TEST")
        .claim("email_verified", true)
        .claim("name", "관리자")
        .claim("groups", List.of("wearly-admins"))
        .claim("amr", List.of("pwd", "otp"));
  }

  static String sign(JWTClaimsSet c, RSAKey k) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(k.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            c);
    jwt.sign(new RSASSASigner(k));
    return jwt.serialize();
  }

  static String token(Consumer<JWTClaimsSet.Builder> change) throws Exception {
    JWTClaimsSet.Builder b = claims();
    change.accept(b);
    return sign(b.build(), key);
  }

  static IdTokenClaims verify(String token) {
    return verify(token, "groups");
  }

  static IdTokenClaims verify(String token, String rolesClaim) {
    return IdTokenVerifier.verify(token, jwks, ISS, CLIENT, NONCE, rolesClaim, NOW);
  }

  static void rejected(String token, Reason reason) {
    assertThatThrownBy(() -> verify(token))
        .isInstanceOf(OidcRejectedException.class)
        .extracting(e -> ((OidcRejectedException) e).reason())
        .isEqualTo(reason);
  }

  @Test
  void 맞는_토큰이면_값을_꺼낸다() throws Exception {
    IdTokenClaims c = verify(token(b -> {}));
    assertThat(c.issuer()).isEqualTo(ISS);
    assertThat(c.subject()).isEqualTo("sub-1");
    assertThat(c.email()).isEqualTo("admin@corp.test");
    assertThat(c.emailVerified()).isTrue();
    assertThat(c.name()).isEqualTo("관리자");
    assertThat(c.groups()).containsExactly("wearly-admins");
    assertThat(c.idpMfa()).isTrue();
    assertThat(c.amrText()).isEqualTo("pwd,otp");
  }

  @Test
  void 서명이_틀리면_거부한다() throws Exception {
    rejected(sign(claims().build(), otherKey), Reason.SIGNATURE);
  }

  @Test
  void none과_HS_알고리즘은_받지_않는다() throws Exception {
    rejected(new PlainJWT(new PlainHeader(), claims().build()).serialize(), Reason.MALFORMED);
    SignedJWT hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
    hs.sign(new MACSigner("0123456789abcdef0123456789abcdef0123456789abcdef"));
    rejected(hs.serialize(), Reason.SIGNATURE);
    rejected("not-a-jwt", Reason.MALFORMED);
  }

  @Test
  void 발급자가_다르면_거부한다() throws Exception {
    rejected(token(b -> b.issuer("http://keycloak:8080/realms/wearly")), Reason.ISSUER);
    rejected(token(b -> b.issuer(ISS + "/")), Reason.ISSUER);
  }

  @Test
  void aud에_client_id가_없거나_azp가_다르면_거부한다() throws Exception {
    rejected(token(b -> b.audience("other").claim("azp", "other")), Reason.AUDIENCE);
    rejected(
        token(b -> b.audience(List.of(CLIENT, "other")).claim("azp", "other")), Reason.AUDIENCE);
    rejected(token(b -> b.audience(List.of(CLIENT, "other")).claim("azp", null)), Reason.AUDIENCE);
    // aud가 여럿이어도 azp가 우리면 받는다
    assertThat(verify(token(b -> b.audience(List.of(CLIENT, "other"))))).isNotNull();
  }

  @Test
  void 만료_발급시각_nonce_sub를_확인한다() throws Exception {
    rejected(token(b -> b.expirationTime(Date.from(NOW.minusSeconds(61)))), Reason.EXPIRED);
    rejected(token(b -> b.expirationTime(null)), Reason.EXPIRED);
    // 시계 차이 60초까지는 받는다
    assertThat(verify(token(b -> b.expirationTime(Date.from(NOW.minusSeconds(30)))))).isNotNull();
    rejected(token(b -> b.issueTime(Date.from(NOW.plusSeconds(120)))), Reason.ISSUED_IN_FUTURE);
    rejected(token(b -> b.issueTime(null)), Reason.MALFORMED);
    rejected(token(b -> b.claim("nonce", "other")), Reason.NONCE);
    rejected(token(b -> b.claim("nonce", null)), Reason.NONCE);
    rejected(token(b -> b.subject(null)), Reason.SUBJECT);
  }

  @Test
  void 그룹_클레임은_중첩_경로와_문자열_하나도_읽는다() throws Exception {
    String t = token(b -> b.claim("realm_access", Map.of("roles", List.of("wearly-admins", "x"))));
    assertThat(verify(t, "realm_access.roles").groups()).containsExactly("wearly-admins", "x");
    assertThat(verify(token(b -> b.claim("groups", "solo"))).groups()).containsExactly("solo");
    assertThat(verify(token(b -> b.claim("groups", null))).groups()).isEmpty();
    assertThat(verify(t, "realm_access.missing").groups()).isEmpty();
  }

  @Test
  void email_verified는_문자열도_받고_없으면_null이다() throws Exception {
    assertThat(verify(token(b -> b.claim("email_verified", "true"))).emailVerified()).isTrue();
    assertThat(verify(token(b -> b.claim("email_verified", "false"))).emailVerified()).isFalse();
    assertThat(verify(token(b -> b.claim("email_verified", null))).emailVerified()).isNull();
  }
}
