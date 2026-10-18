package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.auth.application.port.in.dto.SessionDto;
import app.wearly.auth.application.port.in.dto.SessionSliceDto;
import app.wearly.auth.application.port.in.dto.SessionStatsDto;
import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.application.port.out.SessionStorePort;
import app.wearly.auth.application.port.out.SessionStorePort.SessionSlice;
import app.wearly.auth.application.port.out.SessionStorePort.SessionState;
import app.wearly.auth.domain.AuthEvent;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.LoginSession;
import app.wearly.auth.domain.RefreshToken;
import app.wearly.auth.domain.RefreshTokens;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 세션과 Refresh 토큰 (16 문서 3장). 세션·토큰은 저장소(Valkey)에, 로그인 이력은 DB(tb_auth_event)에 둔다. 회전 규칙(재사용 감지)은
 * 도메인 RefreshToken.check가 정한다: 이미 회전된 토큰이 다시 오면 그 세션을 끊는다.
 */
@Service
public class SessionService implements SessionUseCase {

  private final SessionStorePort store;
  private final AuthEventPort events;
  private final TokenPolicy policy;
  private final Clock clock;

  public SessionService(
      SessionStorePort store, AuthEventPort events, TokenPolicy policy, Clock clock) {
    this.store = store;
    this.events = events;
    this.policy = policy;
    this.clock = clock;
  }

  /** 새 세션의 Refresh 토큰 원문과 세션 ID */
  public record Issued(String rawToken, String sessionId) {}

  /** 회전으로 받은 새 Refresh 토큰 원문과 그 세션 */
  public record Rotated(String rawToken, long userId, String sessionId) {}

  /** 로그인·가입: 새 세션을 만들고 LOGIN을 남긴다 (가입·로그인 트랜잭션 안에서) */
  @Transactional(propagation = Propagation.MANDATORY)
  public Issued start(long userId, ClientInfo client) {
    return start(userId, client, false);
  }

  /** mfaVerified: 관리자 2단계 인증을 확인하고 만드는 세션 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Issued start(long userId, ClientInfo client, boolean mfaVerified) {
    Instant now = clock.instant();
    LoginSession session = LoginSession.start(userId, client, now, mfaVerified);
    String raw = RefreshTokens.newRawToken();
    store.create(
        session,
        RefreshToken.issue(RefreshTokens.hash(raw), userId, session.id(), now),
        policy.refreshTtl());
    events.record(AuthEvent.of(userId, AuthEvent.Type.LOGIN, session.id(), client, now));
    return new Issued(raw, session.id());
  }

  /**
   * 회전. 실패하면 BusinessException을 던지지만 재사용 감지 기록(REFRESH_REUSED)은 커밋해야 하므로 BusinessException에서는 롤백하지
   * 않는다. 같은 토큰으로 동시에 갱신하면 저장소가 한 요청만 회전시키고, 나머지는 다시 읽어 ROTATED를 보고 재사용으로 처리한다(이전 DB 행 잠금과 같은 결과).
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public Rotated rotate(String rawToken, ClientInfo by) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new BusinessException(ErrorCode.REFRESH_INVALID);
    }
    String hash = RefreshTokens.hash(rawToken);
    Instant now = clock.instant();
    RefreshToken current = findOrInvalid(hash);
    rejectUnlessOk(current, by, now);
    String nextRaw = RefreshTokens.newRawToken();
    RefreshToken next =
        RefreshToken.issue(RefreshTokens.hash(nextRaw), current.userId(), current.sessionId(), now);
    if (!store.rotate(hash, next, now, policy.refreshTtl())) {
      // 다른 요청이 먼저 회전했거나 그사이 세션이 끊겼다
      rejectUnlessOk(findOrInvalid(hash), by, now);
      throw new BusinessException(ErrorCode.REFRESH_INVALID);
    }
    return new Rotated(nextRaw, current.userId(), current.sessionId());
  }

  /** 로그아웃: 지금 쓸 수 있는 토큰이면 그 세션을 끊고 LOGOUT을 남긴다. 없거나 이미 바뀐 토큰이면 조용히 끝낸다 */
  @Transactional
  public void logout(String rawToken, ClientInfo by) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    store
        .findToken(RefreshTokens.hash(rawToken))
        .filter(t -> t.status() == RefreshToken.Status.ACTIVE)
        .flatMap(t -> store.delete(t.sessionId()))
        .ifPresent(
            s ->
                events.record(
                    AuthEvent.of(s.userId(), AuthEvent.Type.LOGOUT, s.id(), by, clock.instant())));
  }

  /** 매 요청: 세션이 살아 있고 그 회원의 것인지와 2단계 인증 여부 (lastSeenAt은 1분에 한 번만 쓴다) */
  public SessionState check(long userId, String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return SessionState.GONE;
    }
    return store.touch(sessionId, userId, clock.instant(), LoginSession.SEEN_WRITE_INTERVAL);
  }

  /** 지금 세션을 2단계 인증을 확인한 세션으로 표시한다 (로그인 중 TOTP를 켤 때) */
  public void markMfaVerified(long userId, String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      store.markMfaVerified(sessionId, userId);
    }
  }

  /** 그 세션의 기기 정보 (없으면 빈 값) */
  public Optional<ClientInfo> clientOf(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    return store.findSession(sessionId).map(s -> new ClientInfo(s.client(), s.userAgent(), s.ip()));
  }

  public long refreshTtlSeconds() {
    return policy.refreshTtl().toSeconds();
  }

  @Override
  public List<SessionDto> allSessions() {
    return store.findAll(clock.instant()).stream().map(SessionService::toDto).toList();
  }

  @Override
  public SessionSliceDto recentSessions(long offset, int limit) {
    SessionSlice slice = store.findRecent(clock.instant(), offset, limit);
    return new SessionSliceDto(
        slice.items().stream().map(SessionService::toDto).toList(), slice.total());
  }

  @Override
  public List<SessionDto> userSessions(long userId) {
    return store.findByUser(userId).stream().map(SessionService::toDto).toList();
  }

  @Override
  public Optional<SessionDto> findSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    return store.findSession(sessionId).map(SessionService::toDto);
  }

  @Override
  @Transactional
  public boolean endSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return false;
    }
    Optional<LoginSession> ended = store.delete(sessionId);
    ended.ifPresent(this::recordForced);
    return ended.isPresent();
  }

  @Override
  @Transactional
  public int endUserSessions(long userId) {
    int n = 0;
    for (LoginSession s : store.findByUser(userId)) {
      Optional<LoginSession> ended = store.delete(s.id());
      if (ended.isPresent()) {
        recordForced(ended.get());
        n++;
      }
    }
    return n;
  }

  @Override
  @Transactional
  public int endOtherSessions(long userId, String keepSessionId) {
    int n = 0;
    for (LoginSession s : store.findByUser(userId)) {
      if (s.id().equals(keepSessionId)) {
        continue;
      }
      Optional<LoginSession> ended = store.delete(s.id());
      if (ended.isPresent()) {
        recordForced(ended.get());
        n++;
      }
    }
    return n;
  }

  @Override
  public void dropUserSessions(long userId) {
    for (LoginSession s : store.findByUser(userId)) {
      store.delete(s.id());
    }
  }

  @Override
  public SessionStatsDto stats(Instant activeSince) {
    Instant now = clock.instant();
    return new SessionStatsDto(store.count(now), store.countUsersSeenSince(now, activeSince));
  }

  private RefreshToken findOrInvalid(String hash) {
    return store
        .findToken(hash)
        .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_INVALID));
  }

  /** OK가 아니면 던진다. 재사용이면 그 세션을 끊고 REFRESH_REUSED를 남긴다 */
  private void rejectUnlessOk(RefreshToken token, ClientInfo by, Instant now) {
    switch (token.check()) {
      case OK -> {
        // 회전해도 된다
      }
      case INVALID -> throw new BusinessException(ErrorCode.REFRESH_INVALID);
      case REUSED -> {
        store.delete(token.sessionId());
        events.record(
            AuthEvent.of(
                token.userId(), AuthEvent.Type.REFRESH_REUSED, token.sessionId(), by, now));
        throw new BusinessException(ErrorCode.REFRESH_REUSED);
      }
      default -> throw new IllegalStateException("모르는 토큰 상태: " + token.check());
    }
  }

  private void recordForced(LoginSession s) {
    events.record(AuthEvent.of(AuthEvent.Type.FORCED_LOGOUT, s, clock.instant()));
  }

  private static SessionDto toDto(LoginSession s) {
    return new SessionDto(
        s.id(),
        s.userId(),
        s.client().name(),
        s.userAgent(),
        s.ip(),
        s.createdAt(),
        s.lastRefreshAt(),
        s.lastSeenAt());
  }
}
