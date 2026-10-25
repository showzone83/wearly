package app.wearly.pairing.application.port.out;

import app.wearly.pairing.application.port.out.dto.PairingRowDto;
import app.wearly.pairing.application.port.out.dto.ProxyActionRowDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 연결 조회 (MyBatis). pendingSince보다 먼저 만든 대기는 만료로 보고 세지 않는다 */
public interface PairingQueryPort {

  /** 주인에게 연결된(대기 포함) 돕는 사람 수 */
  long countOpenAsOwner(long ownerId, Instant pendingSince);

  /** 돕는 사람이 맡은(대기 포함) 옷장 수 */
  long countOpenAsHelper(long helperId, Instant pendingSince);

  /** 이 회원이 주인이거나 돕는 사람인 살아 있는 연결, 최근 순 */
  List<PairingRowDto> findOpen(long userId, Instant pendingSince);

  /** 회원 이름. 탈퇴했으면 빈 값 */
  Optional<String> findUserName(long userId);

  /** 연결의 대리 작업 기록, 최근 순 */
  List<ProxyActionRowDto> findActions(long pairingId, int limit);
}
