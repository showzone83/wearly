package app.wearly.pairing.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.pairing.application.port.in.PairingAdminUseCase;
import app.wearly.pairing.application.port.in.dto.PairingAdminEndDto;
import app.wearly.pairing.application.port.out.PairingNoticePort;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingEndReason;
import app.wearly.pairing.domain.PairingNotices;
import app.wearly.pairing.domain.PairingStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 연결 강제 해제 (doc/26 6단계). 관리자 변경 이력은 부르는 admin이 같은 트랜잭션에서 남긴다 */
@Service
public class PairingAdminService implements PairingAdminUseCase {

  private final PairingPort pairings;
  private final PairingNoticePort notices;
  private final Clock clock;

  public PairingAdminService(PairingPort pairings, PairingNoticePort notices, Clock clock) {
    this.pairings = pairings;
    this.notices = notices;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PairingAdminEndDto forceEnd(long pairingId) {
    Pairing p = pairings.find(pairingId).orElseThrow(BusinessException::notFound);
    if (p.status() == PairingStatus.ENDED) {
      throw BusinessException.notFound();
    }
    pairings.save(p.end(PairingEndReason.ADMIN, clock.instant()));
    // 연결된 뒤의 해제만 알린다 (대기 요청은 알리지 않는다, 사용자 해제와 같은 규칙)
    if (p.status() == PairingStatus.ACTIVE) {
      notices.sendAfterCommit(p.ownerId(), p.id(), PairingNotices.endedByAdmin());
      notices.sendAfterCommit(p.helperId(), p.id(), PairingNotices.endedByAdmin());
    }
    return new PairingAdminEndDto(
        p.id(),
        p.ownerId(),
        p.helperId(),
        p.permission() == null ? null : p.permission().name(),
        p.status().name());
  }
}
