package app.wearly.support;

import app.wearly.auth.application.port.out.SessionStorePort;
import app.wearly.auth.domain.LoginSession;
import app.wearly.auth.domain.RefreshToken;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 서비스 단위 테스트용 세션 저장소 (메모리, TTL 없음). Valkey 어댑터와 같은 규칙: 회전은 ACTIVE·현재 토큰일 때만, 끊으면 현재 토큰 REVOKED */
public class InMemorySessionStore implements SessionStorePort {

  public final Map<String, RefreshToken> tokens = new LinkedHashMap<>();
  public final Map<String, LoginSession> sessions = new LinkedHashMap<>();
  public final Map<String, String> currentHash = new LinkedHashMap<>();

  @Override
  public void create(LoginSession session, RefreshToken token, Duration ttl) {
    tokens.put(token.tokenHash(), token);
    sessions.put(session.id(), session);
    currentHash.put(session.id(), token.tokenHash());
  }

  @Override
  public Optional<RefreshToken> findToken(String tokenHash) {
    return Optional.ofNullable(tokens.get(tokenHash));
  }

  @Override
  public boolean rotate(String hash, RefreshToken next, Instant now, Duration ttl) {
    RefreshToken t = tokens.get(hash);
    if (t == null
        || t.status() != RefreshToken.Status.ACTIVE
        || !hash.equals(currentHash.get(t.sessionId()))) {
      return false;
    }
    tokens.put(
        hash,
        new RefreshToken(
            hash,
            t.userId(),
            t.sessionId(),
            RefreshToken.Status.ROTATED,
            next.tokenHash(),
            t.createdAt()));
    tokens.put(next.tokenHash(), next);
    currentHash.put(t.sessionId(), next.tokenHash());
    LoginSession s = sessions.get(t.sessionId());
    sessions.put(
        s.id(),
        new LoginSession(
            s.id(),
            s.userId(),
            s.client(),
            s.userAgent(),
            s.ip(),
            s.createdAt(),
            now,
            now,
            s.mfaVerified()));
    return true;
  }

  @Override
  public Optional<LoginSession> findSession(String sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }

  @Override
  public SessionState touch(String sessionId, long userId, Instant now, Duration minInterval) {
    LoginSession s = sessions.get(sessionId);
    if (s == null || s.userId() != userId) {
      return SessionState.GONE;
    }
    return s.mfaVerified() ? SessionState.ACTIVE_MFA : SessionState.ACTIVE;
  }

  @Override
  public boolean markMfaVerified(String sessionId, long userId) {
    LoginSession s = sessions.get(sessionId);
    if (s == null || s.userId() != userId) {
      return false;
    }
    sessions.put(
        sessionId,
        new LoginSession(
            s.id(),
            s.userId(),
            s.client(),
            s.userAgent(),
            s.ip(),
            s.createdAt(),
            s.lastRefreshAt(),
            s.lastSeenAt(),
            true));
    return true;
  }

  @Override
  public Optional<LoginSession> delete(String sessionId) {
    LoginSession s = sessions.remove(sessionId);
    if (s == null) {
      return Optional.empty();
    }
    String hash = currentHash.remove(sessionId);
    RefreshToken t = tokens.get(hash);
    if (t != null && t.status() == RefreshToken.Status.ACTIVE) {
      tokens.put(
          hash,
          new RefreshToken(
              hash,
              t.userId(),
              t.sessionId(),
              RefreshToken.Status.REVOKED,
              t.replacedBy(),
              t.createdAt()));
    }
    return Optional.of(s);
  }

  @Override
  public List<LoginSession> findByUser(long userId) {
    return sorted().stream().filter(s -> s.userId() == userId).toList();
  }

  @Override
  public List<LoginSession> findAll(Instant now) {
    return sorted();
  }

  @Override
  public SessionSlice findRecent(Instant now, long offset, int limit) {
    List<LoginSession> all = sorted();
    return new SessionSlice(all.stream().skip(offset).limit(limit).toList(), all.size());
  }

  @Override
  public long count(Instant now) {
    return sessions.size();
  }

  @Override
  public long countUsersSeenSince(Instant now, Instant since) {
    return sessions.values().stream()
        .filter(s -> !s.lastSeenAt().isBefore(since))
        .map(LoginSession::userId)
        .distinct()
        .count();
  }

  private List<LoginSession> sorted() {
    return sessions.values().stream()
        .sorted(Comparator.comparing(LoginSession::lastSeenAt).reversed())
        .toList();
  }
}
