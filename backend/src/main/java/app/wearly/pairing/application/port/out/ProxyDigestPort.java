package app.wearly.pairing.application.port.out;

import app.wearly.pairing.application.port.out.dto.ClaimedActionRowDto;
import java.time.Instant;
import java.util.List;

/** 묶음 알림 대상 찾기·가져오기 (MyBatis). 여러 서버가 동시에 돌아도 한 번만 보내도록 가져오면서 표시한다 */
public interface ProxyDigestPort {

  /** 아직 알리지 않은 작업이 있는 연결 중, 마지막 작업이 quietBefore 이전이거나 첫 작업이 waitedBefore 이전인 연결 ID */
  List<Long> findDue(Instant quietBefore, Instant waitedBefore);

  /** 연결의 아직 알리지 않은 작업(upTo까지)을 알림 보낸 것으로 표시하며 돌려준다. 다른 서버가 먼저 가져갔으면 빈 목록 */
  List<ClaimedActionRowDto> claim(long pairingId, Instant upTo, Instant now);
}
