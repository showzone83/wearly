package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.SsoAccountListener;
import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.MfaChallengeDto;
import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.SsoLinkPort;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.SsoGrant;
import app.wearly.auth.domain.SsoLink;
import app.wearly.auth.domain.SsoProvider;
import app.wearly.auth.domain.SsoSettings;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSO 계정 연결·역할 맞추기·토큰 발급 (22 문서 4.2). SsoService가 IdP 확인을 마친 뒤 부른다 (DB 트랜잭션은 여기서만).
 *
 * <ul>
 *   <li>IdP 계정은 발급자 + sub로 찾는다. 처음이면 검증된 이메일로 기존 <b>관리자</b> 회원과 잇고, 없으면 새 관리자 회원(비밀번호 없음)을 만든다. 같은
 *       이메일의 일반 회원(고객) 계정은 관리자로 올리지 않는다 — 직원·고객 계정을 나누고(22 문서 1장) 고객 비밀번호가 관리자 로그인 경로가 되지 않게 한다.
 *   <li>로그인할 때마다 IdP 그룹으로 역할을 맞춘다: 관리자 그룹이면 ADMIN(일반 회원이었으면 올림), 아니면 거부하고 연결된 관리자 회원은 USER로 내린 뒤
 *       세션을 모두 끊는다(마지막 관리자는 내리지 않는다). 바뀐 역할은 SsoAccountListener(admin)가 변경 이력에 남긴다.
 *   <li>거부·연결·역할 변경 기록은 업무 오류로 끝나도 남겨야 하므로 BusinessException에서는 롤백하지 않는다.
 * </ul>
 */
@Service
public class SsoAccountService {

  private static final Logger log = LoggerFactory.getLogger(SsoAccountService.class);

  private final UserUseCase users;
  private final CredentialPort credentials;
  private final SsoLinkPort links;
  private final SessionService sessions;
  private final MfaService mfa;
  private final AccessTokenIssuer accessTokens;
  private final AuthEventPort events;
  private final SsoSettings settings;
  private final List<SsoAccountListener> listeners;
  private final Clock clock;

  public SsoAccountService(
      UserUseCase users,
      CredentialPort credentials,
      SsoLinkPort links,
      SessionService sessions,
      MfaService mfa,
      AccessTokenIssuer accessTokens,
      AuthEventPort events,
      SsoSettings settings,
      List<SsoAccountListener> listeners,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.links = links;
    this.sessions = sessions;
    this.mfa = mfa;
    this.accessTokens = accessTokens;
    this.events = events;
    this.settings = settings;
    this.listeners = List.copyOf(listeners);
    this.clock = clock;
  }

  /**
   * 확인을 마친 ID 토큰으로 관리자 회원을 정한다.
   *
   * @return 회원 ID
   * @throws BusinessException 403 SSO_NOT_ADMIN · SSO_EMAIL_UNVERIFIED · ACCOUNT_SUSPENDED
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public long resolve(SsoProvider provider, IdTokenClaims claims, ClientInfo by) {
    boolean emailTrusted = provider.emailTrusted(claims);
    boolean admin = provider.grantsAdmin(claims.groups(), claims.email(), emailTrusted);
    Optional<SsoLink> link = links.find(claims.issuer(), claims.subject());
    if (!admin) {
      link.ifPresent(l -> deny(l, provider, claims, by));
      log.warn(
          "SSO 거부: 관리자 그룹이 아님 provider={} linkedUserId={} groups={}",
          provider.id(),
          link.map(SsoLink::userId).orElse(null),
          claims.groups());
      throw new BusinessException(ErrorCode.SSO_NOT_ADMIN);
    }
    UserAccountDto user;
    long linkId;
    if (link.isPresent()) {
      user = users.get(link.get().userId());
      linkId = link.get().id();
    } else {
      if (!emailTrusted) {
        log.warn(
            "SSO 거부: 처음 로그인인데 검증된 이메일이 없음 provider={} emailVerified={} hasEmail={}",
            provider.id(),
            claims.emailVerified(),
            claims.email() != null);
        throw new BusinessException(ErrorCode.SSO_EMAIL_UNVERIFIED);
      }
      Linked linked = linkOrCreate(provider, claims);
      user = linked.user();
      linkId = linked.linkId();
    }
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
    }
    if (!user.isAdmin()) {
      user = promote(user, provider);
    }
    links.touch(linkId, claims.email(), provider.id(), now());
    return user.id();
  }

  /**
   * 교환 코드의 회원으로 세션을 만든다. 그사이 정지·역할 해제됐으면 거부한다. trust-idp-mfa가 꺼져 있고 그 관리자에게 앱 2단계 인증이 필요하면 토큰 대신
   * MFA 대기 토큰을 준다(비밀번호 로그인과 같은 다음 단계).
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public LoginResultDto issue(SsoGrant grant, ClientInfo by) {
    UserAccountDto user =
        users
            .find(grant.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SSO_EXCHANGE_INVALID));
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
    }
    if (!user.isAdmin()) {
      throw new BusinessException(ErrorCode.SSO_NOT_ADMIN);
    }
    boolean trusted = settings.trustIdpMfa();
    if (!trusted) {
      Optional<MfaChallengeDto> challenge = mfa.challenge(user, by);
      if (challenge.isPresent()) {
        return LoginResultDto.pending(challenge.get());
      }
    }
    SessionService.Issued issued =
        sessions.startSso(user.id(), by, trusted, grant.providerId(), grant.amr());
    return LoginResultDto.of(
        new AuthResultDto(
            accessTokens.issue(user.id(), user.role(), issued.sessionId()),
            accessTokens.accessTtlSeconds(),
            issued.rawToken(),
            sessions.refreshTtlSeconds(),
            user));
  }

  /** 일반 회원이었던 연결 계정: IdP 관리자 그룹이므로 ADMIN으로 올린다 */
  private UserAccountDto promote(UserAccountDto before, SsoProvider provider) {
    UserAccountDto after =
        users.changeAccess(before.id(), before.status(), UserAccountDto.ROLE_ADMIN);
    listeners.forEach(
        l ->
            l.roleChanged(
                after.id(),
                after.email(),
                after.status(),
                before.role(),
                after.role(),
                provider.id()));
    log.info("SSO: IdP 관리자 그룹이라 관리자로 올림 userId={} provider={}", before.id(), provider.id());
    return after;
  }

  /** 처음 로그인: 같은 이메일의 관리자 회원과 잇거나 새 관리자 회원을 만든다 */
  private Linked linkOrCreate(SsoProvider provider, IdTokenClaims claims) {
    Optional<UserAccountDto> existing =
        credentials
            .findByEmail(LocalCredential.normalizeEmail(claims.email()))
            .flatMap(c -> users.find(c.userId()));
    if (existing.isPresent() && existing.get().isAdmin()) {
      UserAccountDto u = existing.get();
      SsoLink link =
          links.create(
              new SsoLink(
                  null, u.id(), provider.id(), claims.issuer(), claims.subject(), claims.email()),
              now());
      listeners.forEach(l -> l.accountLinked(u.id(), claims.email(), provider.id()));
      log.info("SSO: 기존 관리자 회원과 연결 userId={} provider={}", u.id(), provider.id());
      return new Linked(u, link.id());
    }
    if (existing.isPresent()) {
      log.info(
          "SSO: 같은 이메일의 일반 회원이 있지만 관리자로 올리지 않고 직원 계정을 따로 만듦 customerId={} provider={}",
          existing.get().id(),
          provider.id());
    }
    UserAccountDto created =
        users.create(claims.email(), claims.accountName(), UserAccountDto.ROLE_ADMIN);
    SsoLink link =
        links.create(
            new SsoLink(
                null,
                created.id(),
                provider.id(),
                claims.issuer(),
                claims.subject(),
                claims.email()),
            now());
    listeners.forEach(
        l -> l.accountCreated(created.id(), created.email(), created.name(), provider.id()));
    log.info("SSO: 새 관리자 회원 만듦 userId={} provider={}", created.id(), provider.id());
    return new Linked(created, link.id());
  }

  /** 관리자 그룹에서 빠진 연결 회원: 거부 기록을 남기고, 관리자면 USER로 내리고 세션을 모두 끊는다 */
  private void deny(SsoLink link, SsoProvider provider, IdTokenClaims claims, ClientInfo by) {
    Optional<UserAccountDto> found = users.find(link.userId());
    if (found.isEmpty()) {
      return;
    }
    UserAccountDto u = found.get();
    events.record(
        AuthEvent.sso(
            u.id(), AuthEvent.Type.SSO_DENIED, null, by, now(), provider.id(), claims.amrText()));
    if (!u.isAdmin()) {
      return;
    }
    if (u.isActive() && users.lockActiveAdmins().stream().noneMatch(id -> id != u.id())) {
      log.warn(
          "SSO: 관리자 그룹에서 빠졌지만 마지막 관리자라 역할은 그대로 둠 userId={} provider={}", u.id(), provider.id());
      return;
    }
    UserAccountDto after = users.changeAccess(u.id(), u.status(), UserAccountDto.ROLE_USER);
    int ended = sessions.endUserSessions(u.id());
    listeners.forEach(
        l ->
            l.roleChanged(
                after.id(), after.email(), after.status(), u.role(), after.role(), provider.id()));
    log.warn(
        "SSO: 관리자 그룹에서 빠져 관리자 해제 userId={} provider={} endedSessions={}",
        u.id(),
        provider.id(),
        ended);
  }

  private Instant now() {
    return clock.instant();
  }

  private record Linked(UserAccountDto user, long linkId) {}
}
