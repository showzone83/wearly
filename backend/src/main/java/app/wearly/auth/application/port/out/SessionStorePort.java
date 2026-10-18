package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.LoginSession;
import app.wearly.auth.domain.RefreshToken;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 로그인 세션·Refresh 토큰 저장소 (Valkey, 16 문서 3장). 여러 키를 함께 바꾸는 동작(만들기·회전·끊기)은 원자적이다. 만료는 저장소의 TTL이 맡는다:
 * 세션과 그 토큰은 마지막 회전에서 {@code ttl} 뒤에 사라지고, 회전·폐기된 토큰은 원래 만료까지 남는다(재사용 감지).
 */
public interface SessionStorePort {

  /** 새 세션과 그 첫 토큰을 저장한다 */
  void create(LoginSession session, RefreshToken token, Duration ttl);

  /** 저장된 토큰 (만료됐거나 없으면 빈 값) */
  Optional<RefreshToken> findToken(String tokenHash);

  /**
   * 회전: {@code currentHash}가 아직 ACTIVE이고 세션의 현재 토큰일 때만 ROTATED로 바꾸고 {@code next}를 세션의 현재 토큰으로 둔다
   * (세션 만료 연장, lastRefreshAt·lastSeenAt = now). 같은 토큰으로 동시에 회전하면 한쪽만 true다.
   */
  boolean rotate(String currentHash, RefreshToken next, Instant now, Duration ttl);

  Optional<LoginSession> findSession(String sessionId);

  /**
   * 매 요청 인증: 세션이 살아 있고 그 회원의 것인지와 2단계 인증 여부. lastSeenAt이 {@code minInterval}보다 오래됐으면 now로 고친다(목록 정렬
   * 인덱스도).
   */
  SessionState touch(String sessionId, long userId, Instant now, Duration minInterval);

  /** 살아 있는 그 회원의 세션을 2단계 인증을 확인한 세션으로 표시한다. 없으면 false */
  boolean markMfaVerified(String sessionId, long userId);

  /** 세션을 끊는다: 세션을 지우고 현재 토큰을 REVOKED로. 없으면 빈 값 */
  Optional<LoginSession> delete(String sessionId);

  /** 그 회원의 살아 있는 세션 (lastSeenAt 최신순) */
  List<LoginSession> findByUser(long userId);

  /** 살아 있는 세션 전체 (lastSeenAt 최신순). 검색·필터가 있을 때 쓴다 */
  List<LoginSession> findAll(Instant now);

  /** lastSeenAt 최신순 한 쪽과 살아 있는 세션 수 */
  SessionSlice findRecent(Instant now, long offset, int limit);

  /** 살아 있는 세션 수 */
  long count(Instant now);

  /** lastSeenAt이 since 이후인 살아 있는 세션의 회원 수 */
  long countUsersSeenSince(Instant now, Instant since);

  record SessionSlice(List<LoginSession> items, long total) {}

  /** 매 요청 인증 결과 */
  enum SessionState {
    /** 세션이 없거나(끊김·만료) 다른 회원의 것 */
    GONE,
    /** 살아 있음 */
    ACTIVE,
    /** 살아 있고 2단계 인증을 확인한 세션 */
    ACTIVE_MFA
  }
}
