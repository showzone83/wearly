package app.wearly.auth.adapter.in.web;

import app.wearly.auth.adapter.in.web.request.SsoExchangeRequest;
import app.wearly.auth.adapter.in.web.response.LoginResponse;
import app.wearly.auth.adapter.in.web.response.SsoProvidersResponse;
import app.wearly.auth.application.port.in.SsoUseCase;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.SsoCallbackDto;
import app.wearly.auth.application.port.in.dto.SsoRedirectDto;
import app.wearly.auth.domain.SsoRedirects;
import app.wearly.auth.domain.SsoSettings;
import app.wearly.common.error.TraceIds;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 관리자 사이트 회사 계정 로그인 (22 문서 4.2). 시작·콜백·로그아웃은 브라우저가 이동하는 주소라 302로 답한다. 콜백 결과는 관리자 사이트 주소에 붙인다: 성공
 * {@code ?code=<교환 코드>}, 실패 {@code ?error=<오류 코드>&status=<HTTP 상태>[&reason=…]&traceId=…}.
 */
@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoController {

  /** 시작한 브라우저와 콜백 브라우저가 같은지 확인하는 쿠키 (state 값, HttpOnly, 5분) */
  static final String STATE_COOKIE = "wearly_sso_state";

  static final String COOKIE_PATH = "/api/v1/auth/sso/";

  private final SsoUseCase sso;

  public SsoController(SsoUseCase sso) {
    this.sso = sso;
  }

  /** 켜진 공급자 (로그인 화면 버튼). 꺼져 있으면 { enabled: false, providers: [] } */
  @GetMapping("/providers")
  public SsoProvidersResponse providers() {
    return SsoProvidersResponse.from(sso.providers());
  }

  /** IdP 로그인으로 보낸다. redirect = 관리자 사이트 콜백 주소 (허용 목록에 있어야 함) */
  @GetMapping("/{provider}/start")
  public ResponseEntity<Void> start(
      @PathVariable("provider") String provider,
      @RequestParam(name = "redirect", required = false) String redirect,
      HttpServletRequest http) {
    SsoRedirectDto r = sso.start(provider, redirect, callbackUri(http, provider));
    ResponseEntity.HeadersBuilder<?> res = found(location(r));
    if (r.stateCookie() != null) {
      res.header(HttpHeaders.SET_COOKIE, stateCookie(r.stateCookie(), SsoSettings.STATE_TTL, http));
    }
    return res.build();
  }

  /** IdP가 돌려보내는 주소 (IdP에 이 주소를 redirect URI로 등록한다) */
  @GetMapping("/{provider}/callback")
  public ResponseEntity<Void> callback(
      @PathVariable("provider") String provider,
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "error", required = false) String error,
      @RequestParam(name = "iss", required = false) String iss,
      HttpServletRequest http) {
    ClientDto c = ClientHeaders.from(http);
    SsoRedirectDto r =
        sso.callback(new SsoCallbackDto(provider, code, state, error, iss, cookie(http), c));
    return found(location(r))
        .header(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO, http))
        .build();
  }

  /** 교환 코드 → 토큰 (또는 앱 2단계 인증 대기). 응답 모양은 POST /auth/login과 같다 */
  @PostMapping("/exchange")
  public LoginResponse exchange(
      @RequestBody(required = false) SsoExchangeRequest request, HttpServletRequest http) {
    return LoginResponse.from(
        sso.exchange(request == null ? null : request.code(), ClientHeaders.from(http)));
  }

  /** IdP 로그아웃(end_session_endpoint)으로 보낸다. 지원하지 않는 IdP면 redirect로 바로 */
  @GetMapping("/{provider}/logout")
  public ResponseEntity<Void> logout(
      @PathVariable("provider") String provider,
      @RequestParam(name = "redirect", required = false) String redirect) {
    return found(sso.logoutLocation(provider, redirect)).build();
  }

  private static ResponseEntity.HeadersBuilder<?> found(String location) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(location))
        .cacheControl(CacheControl.noStore());
  }

  /** 성공이면 그대로, 실패면 관리자 사이트 주소에 오류 코드·상태·params·traceId를 붙인다 */
  static String location(SsoRedirectDto r) {
    if (!r.failed()) {
      return r.location();
    }
    Map<String, String> q = new LinkedHashMap<>();
    q.put("error", r.error());
    q.put("status", Integer.toString(r.status()));
    r.params().forEach((k, v) -> q.put(k, v == null ? null : String.valueOf(v)));
    q.put("traceId", TraceIds.current());
    return SsoRedirects.withQuery(r.location(), q);
  }

  /** 요청 주소로 만든 백엔드 콜백 주소 (설정 callback-uri가 있으면 서비스가 그 값을 쓴다) */
  private static String callbackUri(HttpServletRequest http, String provider) {
    return ServletUriComponentsBuilder.fromContextPath(http)
        .path("/api/v1/auth/sso/{provider}/callback")
        .buildAndExpand(provider)
        .toUriString();
  }

  private static String stateCookie(String value, Duration maxAge, HttpServletRequest http) {
    return ResponseCookie.from(STATE_COOKIE, value)
        .httpOnly(true)
        .secure(http.isSecure())
        .sameSite("Lax")
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build()
        .toString();
  }

  private static String cookie(HttpServletRequest http) {
    Cookie[] cookies = http.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie c : cookies) {
      if (STATE_COOKIE.equals(c.getName()) && !c.getValue().isEmpty()) {
        return c.getValue();
      }
    }
    return null;
  }
}
