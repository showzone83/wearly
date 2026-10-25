package app.wearly.pairing.application.port.in;

import app.wearly.pairing.application.port.in.dto.ProxyDigestRunDto;
import java.time.Instant;

/** 돕는 사람의 대리 작업을 주인에게 묶어서 알린다 (doc/26 3단계). 예약 작업이 주기적으로 부른다 */
public interface ProxyDigestUseCase {

  /** 지금 시각 기준으로 보낼 때가 된 묶음 알림을 보낸다 */
  ProxyDigestRunDto sendDue();

  /** now를 정해 보낸다 (시험용) */
  ProxyDigestRunDto sendDue(Instant now);
}
