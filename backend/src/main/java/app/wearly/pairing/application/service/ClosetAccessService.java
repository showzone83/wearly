package app.wearly.pairing.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.pairing.application.port.in.ClosetAccessUseCase;
import app.wearly.pairing.application.port.in.dto.ClosetAccessDto;
import app.wearly.pairing.application.port.in.dto.ProxyAction;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.application.port.out.ProxyActionPort;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingPermission;
import app.wearly.pairing.domain.PairingStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대리 접근 확인·작업 기록 (doc/26 2단계). 옷장·사진·AI 컨텍스트의 대리 경로가 부른다 */
@Service
public class ClosetAccessService implements ClosetAccessUseCase {

  /** 기록에 남기는 옷 이름 길이 (tb_closet_proxy_action.item_name, 옷 이름 상한과 같다) */
  static final int ITEM_NAME_MAX = 40;

  private final PairingPort pairings;
  private final ProxyActionPort actions;
  private final Clock clock;

  public ClosetAccessService(PairingPort pairings, ProxyActionPort actions, Clock clock) {
    this.pairings = pairings;
    this.actions = actions;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ClosetAccessDto requireView(long helperId, long ownerId) {
    return require(helperId, ownerId, PairingPermission.VIEW);
  }

  @Override
  @Transactional(readOnly = true)
  public ClosetAccessDto requireEdit(long helperId, long ownerId) {
    return require(helperId, ownerId, PairingPermission.EDIT);
  }

  private ClosetAccessDto require(long helperId, long ownerId, PairingPermission required) {
    Pairing p =
        pairings
            .findUnended(ownerId, helperId)
            .filter(x -> x.status() == PairingStatus.ACTIVE)
            .orElseThrow(BusinessException::notFound);
    if (!p.permission().allows(required)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return new ClosetAccessDto(p.id(), p.ownerId(), p.helperId());
  }

  @Override
  @Transactional
  public void record(ClosetAccessDto access, ProxyAction action, long itemId, String itemName) {
    String name =
        itemName == null || itemName.length() <= ITEM_NAME_MAX
            ? itemName
            : itemName.substring(0, ITEM_NAME_MAX);
    actions.record(
        access.pairingId(),
        access.ownerId(),
        access.helperId(),
        action.name(),
        itemId,
        name,
        clock.instant());
  }
}
