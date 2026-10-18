package app.wearly.auth.adapter.out.oidc;

import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.OidcRejectedException;
import app.wearly.auth.domain.OidcRejectedException.Reason;
import app.wearly.auth.domain.OidcUnavailableException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.Key;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OIDC ID 토큰 확인 (OIDC Core 3.1.3.7). 서명은 IdP JWKS의 비대칭 키(RS·PS·ES)로만 받는다 — none·HS(클라이언트 비밀로 서명)는
 * 받지 않는다. 그 뒤 iss(설정과 글자 그대로 같음), aud(client_id 포함, 여럿이면 azp = client_id), exp·iat(허용 오차 60초),
 * nonce(시작 때 만든 값), sub를 확인한다.
 */
public final class IdTokenVerifier {

  /** IdP와 서버 시계 차이 허용 */
  public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

  static final Set<JWSAlgorithm> ALGORITHMS =
      Set.of(
          JWSAlgorithm.RS256,
          JWSAlgorithm.RS384,
          JWSAlgorithm.RS512,
          JWSAlgorithm.PS256,
          JWSAlgorithm.PS384,
          JWSAlgorithm.PS512,
          JWSAlgorithm.ES256,
          JWSAlgorithm.ES384,
          JWSAlgorithm.ES512);

  private static final DefaultJWSVerifierFactory VERIFIERS = new DefaultJWSVerifierFactory();

  private IdTokenVerifier() {}

  /**
   * @param keys IdP JWKS (캐시·키 교체는 JWKSource가 맡는다)
   * @param issuer 기대하는 iss
   * @param clientId 기대하는 aud
   * @param rolesClaim 그룹·역할 클레임 (점으로 중첩 경로)
   */
  public static IdTokenClaims verify(
      String idToken,
      JWKSource<SecurityContext> keys,
      String issuer,
      String clientId,
      String nonce,
      String rolesClaim,
      Instant now) {
    SignedJWT jwt;
    try {
      jwt = SignedJWT.parse(idToken);
    } catch (ParseException | RuntimeException e) {
      throw new OidcRejectedException(Reason.MALFORMED, "서명된 JWT가 아님");
    }
    JWSHeader header = jwt.getHeader();
    if (!ALGORITHMS.contains(header.getAlgorithm())) {
      throw new OidcRejectedException(Reason.SIGNATURE, "받지 않는 알고리즘 " + header.getAlgorithm());
    }
    checkSignature(jwt, keys);
    JWTClaimsSet c;
    try {
      c = jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw new OidcRejectedException(Reason.MALFORMED, "클레임을 읽을 수 없음");
    }
    if (issuer == null || !issuer.equals(c.getIssuer())) {
      throw new OidcRejectedException(Reason.ISSUER, "iss=" + c.getIssuer());
    }
    List<String> aud = c.getAudience();
    if (aud == null || !aud.contains(clientId)) {
      throw new OidcRejectedException(Reason.AUDIENCE, "aud=" + aud);
    }
    Object azp = c.getClaim("azp");
    if ((aud.size() > 1 || azp != null) && !clientId.equals(azp)) {
      throw new OidcRejectedException(Reason.AUDIENCE, "azp=" + azp);
    }
    Date exp = c.getExpirationTime();
    if (exp == null || !now.isBefore(exp.toInstant().plus(CLOCK_SKEW))) {
      throw new OidcRejectedException(Reason.EXPIRED, "exp=" + exp);
    }
    Date iat = c.getIssueTime();
    if (iat == null) {
      throw new OidcRejectedException(Reason.MALFORMED, "iat 없음");
    }
    if (iat.toInstant().isAfter(now.plus(CLOCK_SKEW))) {
      throw new OidcRejectedException(Reason.ISSUED_IN_FUTURE, "iat=" + iat);
    }
    Object tokenNonce = c.getClaim("nonce");
    if (nonce == null || !nonce.equals(tokenNonce)) {
      throw new OidcRejectedException(Reason.NONCE, "nonce가 다름");
    }
    String sub = c.getSubject();
    if (sub == null || sub.isBlank() || sub.length() > 255) {
      throw new OidcRejectedException(Reason.SUBJECT, "sub가 없거나 너무 김");
    }
    return new IdTokenClaims(
        c.getIssuer(),
        sub,
        string(c.getClaim("email")),
        bool(c.getClaim("email_verified")),
        firstNonBlank(string(c.getClaim("name")), string(c.getClaim("preferred_username"))),
        strings(path(c.getClaims(), rolesClaim)),
        strings(c.getClaim("amr")));
  }

  private static void checkSignature(SignedJWT jwt, JWKSource<SecurityContext> keys) {
    List<? extends Key> candidates;
    try {
      candidates =
          new JWSVerificationKeySelector<>(jwt.getHeader().getAlgorithm(), keys)
              .selectJWSKeys(jwt.getHeader(), null);
    } catch (KeySourceException e) {
      throw new OidcUnavailableException("JWKS를 받지 못함", e);
    }
    for (Key key : candidates) {
      try {
        JWSVerifier verifier = VERIFIERS.createJWSVerifier(jwt.getHeader(), key);
        if (jwt.verify(verifier)) {
          return;
        }
      } catch (JOSEException e) {
        // 이 키로는 확인할 수 없음 → 다음 키
        continue;
      }
    }
    throw new OidcRejectedException(Reason.SIGNATURE, "맞는 키가 없음 kid=" + jwt.getHeader().getKeyID());
  }

  /** "a.b.c" 경로의 값 (중간이 객체가 아니면 null) */
  static Object path(Map<String, Object> claims, String path) {
    Object cur = claims;
    for (String part : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> m)) {
        return null;
      }
      cur = m.get(part);
    }
    return cur;
  }

  /** 문자열 목록 (배열·문자열 하나 모두 받는다) */
  static List<String> strings(Object v) {
    List<String> out = new ArrayList<>();
    if (v instanceof Collection<?> list) {
      for (Object o : list) {
        if (o instanceof String s && !s.isBlank()) {
          out.add(s);
        }
      }
    } else if (v instanceof String s && !s.isBlank()) {
      out.add(s);
    }
    return out;
  }

  private static String string(Object v) {
    return v instanceof String s && !s.isBlank() ? s : null;
  }

  /** email_verified: true·false, 또는 "true"·"false" 문자열(일부 IdP). 없으면 null */
  private static Boolean bool(Object v) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof String s) {
      return "true".equalsIgnoreCase(s)
          ? Boolean.TRUE
          : "false".equalsIgnoreCase(s) ? Boolean.FALSE : null;
    }
    return null;
  }

  private static String firstNonBlank(String a, String b) {
    return a != null ? a : b;
  }
}
