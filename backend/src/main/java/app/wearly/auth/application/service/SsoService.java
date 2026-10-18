package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.SsoUseCase;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.SsoCallbackDto;
import app.wearly.auth.application.port.in.dto.SsoProviderDto;
import app.wearly.auth.application.port.in.dto.SsoRedirectDto;
import app.wearly.auth.application.port.out.OidcPort;
import app.wearly.auth.application.port.out.SsoStorePort;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.OidcMetadata;
import app.wearly.auth.domain.OidcRejectedException;
import app.wearly.auth.domain.OidcUnavailableException;
import app.wearly.auth.domain.SsoGrant;
import app.wearly.auth.domain.SsoProvider;
import app.wearly.auth.domain.SsoRedirects;
import app.wearly.auth.domain.SsoSecrets;
import app.wearly.auth.domain.SsoSettings;
import app.wearly.auth.domain.SsoState;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 관리자 사이트 SSO 흐름 (22 문서 4.2). 서버가 주도하는 OIDC Authorization Code + PKCE(S256):
 *
 * <pre>
 * 관리자 사이트 ─GET /auth/sso/{p}/start?redirect=─▶ 백엔드: state·nonce·verifier 저장(5분),
 *          state 쿠키 ─302─▶ IdP authorize
 * IdP ─302─▶ GET /auth/sso/{p}/callback?code&state ─▶ 백엔드: state 꺼내기(1회)·쿠키 확인 → 토큰 교환 → ID 토큰 확인
 *          → 계정 연결·관리자 그룹 확인(SsoAccountService) → 교환 코드(1분) ─302─▶ 관리자 사이트 ?code=
 * 관리자 사이트 ─POST /auth/sso/exchange {code}─▶ 우리 Access·Refresh 토큰 (교환 코드 1회)
 * </pre>
 *
 * 콜백의 실패는 JSON 대신 관리자 사이트 주소에 오류 코드를 붙여 보낸다(브라우저 이동 중이므로). 돌아갈 주소를 모르면(state가 없음) 그 공급자 허용 목록의 첫
 * 주소로 보낸다.
 */
@Service
public class SsoService implements SsoUseCase {

  private static final Logger log = LoggerFactory.getLogger(SsoService.class);

  /** IdP 오류 코드(OAuth error)를 params로 넘길 때 받는 모양. 그 밖은 "other" */
  private static final java.util.regex.Pattern IDP_ERROR =
      java.util.regex.Pattern.compile("^[a-z_]{1,40}$");

  /** 관리자 사이트 SSO의 기기 구분 (세션·기록은 늘 ADMIN) */
  private static final String ADMIN_CLIENT = "admin";

  private final SsoSettings settings;
  private final OidcPort oidc;
  private final SsoStorePort store;
  private final SsoAccountService accounts;
  private final Clock clock;

  public SsoService(
      SsoSettings settings,
      OidcPort oidc,
      SsoStorePort store,
      SsoAccountService accounts,
      Clock clock) {
    this.settings = settings;
    this.oidc = oidc;
    this.store = store;
    this.accounts = accounts;
    this.clock = clock;
  }

  @Override
  public List<SsoProviderDto> providers() {
    return settings.visible().stream()
        .map(p -> new SsoProviderDto(p.id(), p.displayName()))
        .toList();
  }

  @Override
  public SsoRedirectDto start(String providerId, String redirect, String callbackUri) {
    SsoProvider p = provider(providerId);
    String back = allowed(p, redirect);
    OidcMetadata m;
    try {
      m = oidc.metadata(p);
    } catch (OidcUnavailableException e) {
      logUnavailable(p, "start", e);
      return failure(back, ErrorCode.SSO_PROVIDER_UNAVAILABLE, Map.of());
    }
    String state = SsoSecrets.newValue();
    String nonce = SsoSecrets.newValue();
    String verifier = SsoSecrets.newValue();
    String callback = p.callbackUri() != null ? p.callbackUri() : callbackUri;
    store.saveState(
        SsoSecrets.hash(state),
        new SsoState(p.id(), nonce, verifier, back, callback, now()),
        SsoSettings.STATE_TTL);
    Map<String, String> q = new LinkedHashMap<>();
    q.put("response_type", "code");
    q.put("client_id", p.clientId());
    q.put("redirect_uri", callback);
    q.put("scope", p.scopeParam());
    q.put("state", state);
    q.put("nonce", nonce);
    q.put("code_challenge", SsoSecrets.s256(verifier));
    q.put("code_challenge_method", "S256");
    return new SsoRedirectDto(
        SsoRedirects.withQuery(m.authorizationEndpoint(), q), state, null, 0, Map.of());
  }

  @Override
  public SsoRedirectDto callback(SsoCallbackDto c) {
    SsoProvider p = provider(c.providerId());
    Optional<SsoState> found =
        SsoSecrets.isFormat(c.state())
            ? store.takeState(SsoSecrets.hash(c.state())).filter(s -> s.providerId().equals(p.id()))
            : Optional.empty();
    if (found.isEmpty()) {
      log.warn("SSO 콜백 거부: state가 없거나 만료·사용됨 provider={}", p.id());
      return failure(p.defaultRedirect(), ErrorCode.SSO_STATE_INVALID, Map.of());
    }
    SsoState st = found.get();
    if (!sameValue(c.cookieState(), c.state())) {
      // 다른 브라우저에서 시작한 로그인(로그인 CSRF)이거나 쿠키가 막힘
      log.warn("SSO 콜백 거부: state 쿠키가 없거나 다름 provider={}", p.id());
      return failure(st.redirect(), ErrorCode.SSO_STATE_INVALID, Map.of());
    }
    if (c.error() != null && !c.error().isBlank()) {
      String reason = IDP_ERROR.matcher(c.error()).matches() ? c.error() : "other";
      log.warn("SSO 콜백: IdP 오류 provider={} error={}", p.id(), reason);
      return failure(st.redirect(), ErrorCode.SSO_IDP_ERROR, Map.of("reason", reason));
    }
    if (c.issuer() != null && !c.issuer().equals(p.issuer())) {
      // RFC 9207: 다른 IdP의 응답이 섞여 들어옴(mix-up)
      log.warn("SSO 콜백 거부: 인가 응답의 iss가 설정과 다름 provider={}", p.id());
      return failure(st.redirect(), ErrorCode.SSO_TOKEN_INVALID, Map.of());
    }
    if (c.code() == null || c.code().isBlank() || c.code().length() > 4096) {
      log.warn("SSO 콜백 거부: 인가 코드 없음 provider={}", p.id());
      return failure(st.redirect(), ErrorCode.SSO_TOKEN_INVALID, Map.of());
    }
    IdTokenClaims claims;
    try {
      OidcMetadata m = oidc.metadata(p);
      String idToken = oidc.exchangeCode(p, m, c.code(), st.callbackUri(), st.codeVerifier());
      claims = oidc.verify(p, m, idToken, st.nonce(), now());
    } catch (OidcUnavailableException e) {
      logUnavailable(p, "callback", e);
      return failure(st.redirect(), ErrorCode.SSO_PROVIDER_UNAVAILABLE, Map.of());
    } catch (OidcRejectedException e) {
      logRejected(p, e);
      return failure(st.redirect(), ErrorCode.SSO_TOKEN_INVALID, Map.of());
    }
    ClientInfo by = admin(c.client());
    long userId;
    try {
      userId = accounts.resolve(p, claims, by);
    } catch (BusinessException e) {
      log.warn("SSO 로그인 거부 code={} provider={}", e.code(), p.id());
      return failure(st.redirect(), e.code(), e.params());
    } catch (RuntimeException e) {
      logSystem(p, e);
      return failure(st.redirect(), ErrorCode.INTERNAL_ERROR, Map.of());
    }
    String code = SsoSecrets.newValue();
    store.saveGrant(
        SsoSecrets.hash(code),
        new SsoGrant(userId, p.id(), claims.amrText(), now()),
        SsoSettings.EXCHANGE_TTL);
    log.info(
        "SSO 콜백 성공 provider={} userId={} idpMfa={} amr={}",
        p.id(),
        userId,
        claims.idpMfa(),
        claims.amrText());
    return SsoRedirectDto.to(SsoRedirects.withQuery(st.redirect(), Map.of("code", code)));
  }

  @Override
  public LoginResultDto exchange(String code, ClientDto client) {
    if (!settings.enabled()) {
      throw BusinessException.notFound();
    }
    SsoGrant grant =
        (SsoSecrets.isFormat(code)
                ? store.takeGrant(SsoSecrets.hash(code))
                : Optional.<SsoGrant>empty())
            .orElseThrow(() -> new BusinessException(ErrorCode.SSO_EXCHANGE_INVALID));
    return accounts.issue(grant, admin(client));
  }

  @Override
  public String logoutLocation(String providerId, String redirect) {
    SsoProvider p = provider(providerId);
    String back = allowed(p, redirect);
    OidcMetadata m;
    try {
      m = oidc.metadata(p);
    } catch (OidcUnavailableException e) {
      logUnavailable(p, "logout", e);
      return back;
    }
    if (m.endSessionEndpoint() == null) {
      return back;
    }
    Map<String, String> q = new LinkedHashMap<>();
    q.put("client_id", p.clientId());
    q.put("post_logout_redirect_uri", back);
    return SsoRedirects.withQuery(m.endSessionEndpoint(), q);
  }

  // ── 내부 ──

  /** 꺼져 있거나 없는 공급자면 404 */
  private SsoProvider provider(String id) {
    return settings.find(id).orElseThrow(BusinessException::notFound);
  }

  /** 허용 목록에 없는 돌아갈 주소는 400 (열린 리디렉트 방지) */
  private static String allowed(SsoProvider p, String redirect) {
    return p.allowedRedirect(redirect)
        .orElseThrow(() -> BusinessException.validation(FieldError.notAllowed("redirect")));
  }

  private static SsoRedirectDto failure(String back, ErrorCode code, Map<String, Object> params) {
    return new SsoRedirectDto(back, null, code.name(), code.status(), params);
  }

  /** 시간 차이 없이 같은 값인지 (state 쿠키 확인) */
  private static boolean sameValue(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static ClientInfo admin(ClientDto c) {
    ClientDto v = c == null ? ClientDto.UNKNOWN : c;
    return ClientInfo.of(ADMIN_CLIENT, v.userAgent(), v.ip());
  }

  private Instant now() {
    return clock.instant();
  }

  private static void logUnavailable(SsoProvider p, String step, OidcUnavailableException e) {
    log.warn("SSO IdP에 닿지 못함 provider={} step={} cause={}", p.id(), step, e.getMessage());
  }

  private static void logRejected(SsoProvider p, OidcRejectedException e) {
    log.warn("SSO 토큰 거부 provider={} reason={} detail={}", p.id(), e.reason(), e.getMessage());
  }

  private static void logSystem(SsoProvider p, RuntimeException e) {
    log.error("SSO 계정 처리 중 시스템 오류 provider={}", p.id(), e);
  }
}
