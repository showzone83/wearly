package app.wearly.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 가짜 OIDC IdP (JDK HttpServer). discovery·JWKS·token 엔드포인트를 흉내 낸다. 테스트는 백엔드가 준 authorize 주소를 {@link
 * #authorize}에 넘겨 "로그인한 것처럼" 인가 코드를 받는다. token 엔드포인트는 클라이언트 인증(Basic)·redirect_uri·PKCE(S256)를
 * 확인하고, 로그인 때 정한 클레임으로 RS256 서명한 ID 토큰을 준다.
 */
public class FakeOidcServer implements AutoCloseable {

  public static final String CLIENT_ID = "wearly-admin-test";
  public static final String CLIENT_SECRET = "test-client-secret";
  static final String REALM = "/realms/test";

  /** 로그인할 IdP 계정 (ID 토큰 클레임) */
  public static final class Account {
    public String sub = UUID.randomUUID().toString();
    public String email;
    public Object emailVerified = Boolean.TRUE;
    public String name = "SSO 관리자";
    public List<String> groups = List.of("wearly-admins");
    public List<String> amr = List.of("pwd", "otp");

    /** 서명 직전에 클레임을 바꾼다 (실패 경우 시험) */
    public Consumer<JWTClaimsSet.Builder> tamper = b -> {};

    public Account email(String v) {
      this.email = v;
      return this;
    }

    public Account groups(String... v) {
      this.groups = List.of(v);
      return this;
    }
  }

  private record Pending(Account account, String nonce, String challenge, String redirectUri) {}

  private final HttpServer server;
  private final RSAKey key;
  private final Map<String, Pending> codes = new ConcurrentHashMap<>();
  private volatile int tokenStatus = 200;

  public FakeOidcServer() {
    try {
      key = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException | JOSEException e) {
      throw new IllegalStateException(e);
    }
    server.createContext(REALM + "/.well-known/openid-configuration", this::discovery);
    server.createContext(REALM + "/certs", this::certs);
    server.createContext(REALM + "/token", this::token);
    server.start();
  }

  public String issuer() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + REALM;
  }

  /** token 엔드포인트가 이 상태로 실패하게 한다 (200이면 정상) */
  public void tokenStatus(int status) {
    this.tokenStatus = status;
  }

  /**
   * 백엔드가 보낸 authorize 주소의 값(state·nonce·code_challenge·redirect_uri)을 확인하고 그 계정으로 로그인한 인가 코드를 만든다.
   */
  public String authorize(String authorizeUrl, Account account) {
    Map<String, String> q = query(URI.create(authorizeUrl).getRawQuery());
    if (!"code".equals(q.get("response_type"))
        || !CLIENT_ID.equals(q.get("client_id"))
        || !"S256".equals(q.get("code_challenge_method"))
        || !q.getOrDefault("scope", "").contains("openid")) {
      throw new IllegalStateException("authorize 요청 값이 틀림: " + q);
    }
    String code = "code-" + UUID.randomUUID();
    codes.put(
        code, new Pending(account, q.get("nonce"), q.get("code_challenge"), q.get("redirect_uri")));
    return code;
  }

  /** 다른 키로 서명한 ID 토큰을 만들 때 쓰는 키 (서명 실패 시험) */
  public String sign(JWTClaimsSet claims, RSAKey signingKey) {
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
              claims);
      jwt.sign(new RSASSASigner(signingKey));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  // ── 엔드포인트 ──

  private void discovery(HttpExchange ex) throws IOException {
    String iss = issuer();
    String json =
        "{\"issuer\":\""
            + iss
            + "\",\"authorization_endpoint\":\""
            + iss
            + "/auth\",\"token_endpoint\":\""
            + iss
            + "/token\",\"jwks_uri\":\""
            + iss
            + "/certs\",\"end_session_endpoint\":\""
            + iss
            + "/logout\",\"token_endpoint_auth_methods_supported\":"
            + "[\"client_secret_basic\",\"client_secret_post\"]}";
    respond(ex, 200, json);
  }

  private void certs(HttpExchange ex) throws IOException {
    respond(ex, 200, new JWKSet(key.toPublicJWK()).toString());
  }

  private void token(HttpExchange ex) throws IOException {
    Map<String, String> form =
        query(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    if (tokenStatus != 200) {
      respond(ex, tokenStatus, "{\"error\":\"server_error\"}");
      return;
    }
    String basic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    if (!basic.equals(ex.getRequestHeaders().getFirst("Authorization"))) {
      respond(ex, 401, "{\"error\":\"invalid_client\"}");
      return;
    }
    Pending p = codes.remove(form.getOrDefault("code", ""));
    if (p == null
        || !"authorization_code".equals(form.get("grant_type"))
        || !p.redirectUri().equals(form.get("redirect_uri"))
        || !p.challenge().equals(s256(form.getOrDefault("code_verifier", "")))) {
      respond(ex, 400, "{\"error\":\"invalid_grant\"}");
      return;
    }
    respond(
        ex,
        200,
        "{\"access_token\":\"idp-access\",\"token_type\":\"Bearer\",\"expires_in\":300,"
            + "\"id_token\":\""
            + idToken(p.account(), p.nonce())
            + "\"}");
  }

  private String idToken(Account a, String nonce) {
    Instant now = Instant.now();
    JWTClaimsSet.Builder b =
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject(a.sub)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("azp", CLIENT_ID)
            .claim("nonce", nonce)
            .claim("name", a.name)
            .claim("groups", a.groups)
            .claim("amr", a.amr);
    if (a.email != null) {
      b.claim("email", a.email);
    }
    if (a.emailVerified != null) {
      b.claim("email_verified", a.emailVerified);
    }
    a.tamper.accept(b);
    return sign(b.build(), key);
  }

  private static void respond(HttpExchange ex, int status, String body) throws IOException {
    byte[] out = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, out.length);
    ex.getResponseBody().write(out);
    ex.close();
  }

  public static Map<String, String> query(String raw) {
    Map<String, String> q = new HashMap<>();
    if (raw == null || raw.isEmpty()) {
      return q;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String k = eq < 0 ? pair : pair.substring(0, eq);
      String v = eq < 0 ? "" : pair.substring(eq + 1);
      q.put(
          URLDecoder.decode(k, StandardCharsets.UTF_8),
          URLDecoder.decode(v, StandardCharsets.UTF_8));
    }
    return q;
  }

  static String s256(String verifier) {
    try {
      byte[] d =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
