package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.SessionDto;
import app.wearly.auth.application.port.in.dto.SessionSliceDto;
import app.wearly.auth.application.port.in.dto.SessionStatsDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 로그인 세션 조회·끊기 (16 문서 4장). 관리자 컨텍스트(로그인 사용자 화면·회원 정지·대시보드)와 회원 탈퇴가 쓴다. 회원 이메일·이름은 여기 없다(부르는 쪽이 회원
 * 정보와 합친다).
 */
public interface SessionUseCase {

  /** 살아 있는 세션 전체 (lastSeenAt 최신순). 검색·필터는 부르는 쪽이 한다 */
  List<SessionDto> allSessions();

  /** lastSeenAt 최신순 한 쪽과 살아 있는 세션 수 */
  SessionSliceDto recentSessions(long offset, int limit);

  /** 그 회원의 살아 있는 세션 (lastSeenAt 최신순) */
  List<SessionDto> userSessions(long userId);

  Optional<SessionDto> findSession(String sessionId);

  /** 강제 로그아웃: 세션 하나를 끊고 FORCED_LOGOUT을 남긴다. 그 세션의 Access 토큰은 다음 요청부터 401. 없으면 false */
  boolean endSession(String sessionId);

  /** 강제 로그아웃·정지: 그 회원의 세션을 모두 끊고 세션마다 FORCED_LOGOUT을 남긴다. @return 끊은 세션 수 */
  int endUserSessions(long userId);

  /**
   * 비밀번호 변경·자기 2단계 인증 초기화: keepSessionId(지금 세션)만 남기고 그 회원의 다른 세션을 모두 끊는다(세션마다 FORCED_LOGOUT).
   *
   * @return 끊은 세션 수
   */
  int endOtherSessions(long userId, String keepSessionId);

  /** 탈퇴: 기록 없이 그 회원의 세션을 모두 지운다 (회원 행과 이력이 함께 지워지므로) */
  void dropUserSessions(long userId);

  /** 살아 있는 세션 수, activeSince 이후 활동한(lastSeenAt) 회원 수 */
  SessionStatsDto stats(Instant activeSince);
}
