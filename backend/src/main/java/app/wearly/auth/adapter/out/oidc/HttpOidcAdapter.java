package app.wearly.auth.adapter.out.oidc;

import app.wearly.auth.application.port.out.OidcPort;
import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.OidcMetadata;
import app.wearly.auth.domain.OidcRejectedException;
import app.wearly.auth.domain.OidcRejectedException.Reason;
import app.wearly.auth.domain.OidcUnavailableException;
import app.wearly.auth.domain.SsoProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OidcPort 구현: JDK HttpClient로 discovery·token 엔드포인트를 부르고, ID 토큰은 Nimbus로 확인한다(IdTokenVerifier).
 *
 * <ul>
 *   <li>discovery 문서는 공급자마다 1시간 캐시한다. 문서의 issuer가 설정과 다르면 쓰지 않는다(설정 오류).
 *   <li>JWKS는 Nimbus JWKSource가 캐시하고, 모르는 kid가 오면 다시 받는다(키 교체).
 *   <li>서버끼리 부르는 주소(discovery·token·JWKS)는 backchannel 바꾸기 규칙을 거친다. docker compose의 백엔드는 브라우저가 쓰는
 *       localhost:8180 대신 keycloak:8080으로 IdP에 닿는다. 브라우저가 가는 주소(authorize·end_session)와 iss는 바꾸지
 *       않는다.
 * </ul>
 */
public class HttpOidcAdapter implements OidcPort {

  /** discovery 문서를 다시 받는 간격 */
  static final Duration METADATA_TTL = Duration.ofHours(1);

  /** IdP 응답 본문 최대 크기 (discovery·token·JWKS) */
  static final int MAX_BODY = 512 * 1024;

  /** 서버끼리 부를 때 바꿀 주소 앞부분 */
  public record Rewrite(String from, String to) {}

  private final HttpClient http;
  private final Duration timeout;
  private final List<Rewrite> rewrites;
  private final Clock clock;
  private final Map<String, Cached> metadata = new ConcurrentHashMap<>();
  private final Map<String, JWKSource<SecurityContext>> jwks = new ConcurrentHashMap<>();

  private record Cached(String discoveryUri, OidcMetadata value, Instant fetchedAt) {}

  public HttpOidcAdapter(Duration timeout, List<Rewrite> rewrites, Clock clock) {
    this.timeout = timeout;
    this.rewrites = List.copyOf(rewrites);
    this.clock = clock;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public OidcMetadata metadata(SsoProvider provider) {
    Instant now = clock.instant();
    Cached c = metadata.get(provider.id());
    if (c != null
        && c.discoveryUri().equals(provider.discoveryUri())
        && c.fetchedAt().plus(METADATA_TTL).isAfter(now)) {
      return c.value();
    }
    OidcMetadata m = fetchMetadata(provider);
    metadata.put(provider.id(), new Cached(provider.discoveryUri(), m, now));
    return m;
  }

  @Override
  public String exchangeCode(
      SsoProvider provider,
      OidcMetadata metadata,
      String code,
      String callbackUri,
      String verifier) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "authorization_code");
    form.put("code", code);
    form.put("redirect_uri", callbackUri);
    form.put("code_verifier", verifier);
    HttpRequest.Builder req =
        HttpRequest.newBuilder(backchannel(metadata.tokenEndpoint()))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded");
    if (metadata.useBasicAuth()) {
      // RFC 6749 2.3.1: client_id·비밀을 form 인코딩한 뒤 Basic으로
      String basic = enc(provider.clientId()) + ":" + enc(provider.clientSecret());
      req.header(
          "Authorization",
          "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
    } else {
      form.put("client_id", provider.clientId());
      form.put("client_secret", provider.clientSecret());
    }
    String body =
        form.entrySet().stream()
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .collect(Collectors.joining("&"));
    HttpResponse<String> res = send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build());
    Map<String, Object> json = parse(res.body(), res.statusCode() >= 400 && res.statusCode() < 500);
    if (res.statusCode() >= 500) {
      throw new OidcUnavailableException("토큰 엔드포인트 " + res.statusCode());
    }
    if (res.statusCode() >= 400) {
      // invalid_grant(코드 만료·재사용·verifier 불일치), invalid_client(비밀 틀림) 등
      Object error = json == null ? null : json.get("error");
      throw new OidcRejectedException(
          Reason.TOKEN_REQUEST, "status=" + res.statusCode() + " error=" + error);
    }
    Object idToken = json == null ? null : json.get("id_token");
    if (!(idToken instanceof String s) || s.isBlank()) {
      throw new OidcRejectedException(Reason.NO_ID_TOKEN, "응답에 id_token이 없음 (scope에 openid?)");
    }
    return s;
  }

  @Override
  public IdTokenClaims verify(
      SsoProvider provider, OidcMetadata metadata, String idToken, String nonce, Instant now) {
    return IdTokenVerifier.verify(
        idToken,
        keys(metadata.jwksUri()),
        provider.issuer(),
        provider.clientId(),
        nonce,
        provider.rolesClaim(),
        now);
  }

  // ── 내부 ──

  private OidcMetadata fetchMetadata(SsoProvider provider) {
    HttpResponse<String> res =
        send(
            HttpRequest.newBuilder(backchannel(provider.discoveryUri()))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build());
    if (res.statusCode() != 200) {
      throw new OidcUnavailableException("discovery " + res.statusCode());
    }
    Map<String, Object> json = parse(res.body(), false);
    String issuer = str(json.get("issuer"));
    if (!provider.issuer().equals(issuer)) {
      // OIDC Discovery 4.3: 문서의 issuer는 설정한 발급자와 같아야 한다. Keycloak이면 KC_HOSTNAME을 확인한다
      throw new OidcUnavailableException(
          "discovery issuer가 설정과 다름: 문서=" + issuer + " 설정=" + provider.issuer());
    }
    String authorization = str(json.get("authorization_endpoint"));
    String token = str(json.get("token_endpoint"));
    String jwksUri = str(json.get("jwks_uri"));
    if (authorization == null || token == null || jwksUri == null) {
      throw new OidcUnavailableException("discovery에 authorization·token·jwks 주소가 없음");
    }
    return new OidcMetadata(
        issuer,
        authorization,
        token,
        jwksUri,
        str(json.get("end_session_endpoint")),
        IdTokenVerifier.strings(json.get("token_endpoint_auth_methods_supported")));
  }

  private JWKSource<SecurityContext> keys(String jwksUri) {
    return jwks.computeIfAbsent(
        jwksUri,
        uri -> {
          int ms = (int) timeout.toMillis();
          try {
            URL url = backchannel(uri).toURL();
            return JWKSourceBuilder.create(url, new DefaultResourceRetriever(ms, ms, MAX_BODY))
                .build();
          } catch (MalformedURLException e) {
            throw new OidcUnavailableException("jwks_uri 형식이 틀림", e);
          }
        });
  }

  /** 서버끼리 부르는 주소: 규칙의 from으로 시작하면 to로 바꾼다 */
  URI backchannel(String url) {
    String u = url;
    for (Rewrite r : rewrites) {
      if (u.startsWith(r.from())) {
        u = r.to() + u.substring(r.from().length());
        break;
      }
    }
    try {
      URI uri = URI.create(u);
      if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
        throw new OidcUnavailableException("http·https 주소가 아님");
      }
      return uri;
    } catch (IllegalArgumentException e) {
      throw new OidcUnavailableException("주소 형식이 틀림", e);
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (res.body() != null && res.body().length() > MAX_BODY) {
        throw new OidcUnavailableException("응답이 너무 큼");
      }
      return res;
    } catch (IOException e) {
      throw new OidcUnavailableException("연결 실패 " + request.uri().getHost(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OidcUnavailableException("중단됨", e);
    }
  }

  /** JSON 객체. lenient면 깨진 본문을 null로 (오류 응답) */
  private static Map<String, Object> parse(String body, boolean lenient) {
    try {
      return JSONObjectUtils.parse(body == null ? "" : body);
    } catch (ParseException e) {
      if (lenient) {
        return null;
      }
      throw new OidcUnavailableException("JSON이 아닌 응답", e);
    }
  }

  private static String str(Object v) {
    return v instanceof String s && !s.isBlank() ? s : null;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
