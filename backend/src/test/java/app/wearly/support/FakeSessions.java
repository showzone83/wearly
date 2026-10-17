package app.wearly.support;

import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.auth.application.port.in.dto.SessionDto;
import app.wearly.auth.application.port.in.dto.SessionSliceDto;
import app.wearly.auth.application.port.in.dto.SessionStatsDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 서비스 단위 테스트용 auth 컨텍스트 세션 입력 포트 (메모리). 끊은 회원·세션을 기록한다 */
public class FakeSessions implements SessionUseCase {

  public final List<SessionDto> rows = new ArrayList<>();
  public final List<Long> endedUsers = new ArrayList<>();
  public final List<String> endedSessions = new ArrayList<>();

  public SessionDto add(String sessionId, long userId, String client, Instant lastSeenAt) {
    SessionDto s =
        new SessionDto(sessionId, userId, client, "ua", "10.0.0.1", lastSeenAt, null, lastSeenAt);
    rows.add(s);
    return s;
  }

  private List<SessionDto> sorted() {
    return rows.stream().sorted(Comparator.comparing(SessionDto::lastSeenAt).reversed()).toList();
  }

  @Override
  public List<SessionDto> allSessions() {
    return sorted();
  }

  @Override
  public SessionSliceDto recentSessions(long offset, int limit) {
    return new SessionSliceDto(sorted().stream().skip(offset).limit(limit).toList(), rows.size());
  }

  @Override
  public List<SessionDto> userSessions(long userId) {
    return sorted().stream().filter(s -> s.userId() == userId).toList();
  }

  @Override
  public Optional<SessionDto> findSession(String sessionId) {
    return rows.stream().filter(s -> s.sessionId().equals(sessionId)).findFirst();
  }

  @Override
  public boolean endSession(String sessionId) {
    endedSessions.add(sessionId);
    return rows.removeIf(s -> s.sessionId().equals(sessionId));
  }

  @Override
  public int endUserSessions(long userId) {
    endedUsers.add(userId);
    int before = rows.size();
    rows.removeIf(s -> s.userId() == userId);
    return before - rows.size();
  }

  @Override
  public void dropUserSessions(long userId) {
    rows.removeIf(s -> s.userId() == userId);
  }

  @Override
  public SessionStatsDto stats(Instant activeSince) {
    return new SessionStatsDto(
        rows.size(),
        rows.stream()
            .filter(s -> !s.lastSeenAt().isBefore(activeSince))
            .map(SessionDto::userId)
            .distinct()
            .count());
  }
}
