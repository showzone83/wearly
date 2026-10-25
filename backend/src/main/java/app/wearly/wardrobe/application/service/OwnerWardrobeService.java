package app.wearly.wardrobe.application.service;

import app.wearly.pairing.application.port.in.ClosetAccessUseCase;
import app.wearly.pairing.application.port.in.dto.ClosetAccessDto;
import app.wearly.pairing.application.port.in.dto.ProxyAction;
import app.wearly.wardrobe.application.port.in.OwnerWardrobeUseCase;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 옷장 도우미의 대리 작업 (doc/26 2단계). 연결·권한은 pairing 컨텍스트의 입력 포트로 확인하고, 옷장 일은 {@link WardrobeUseCase}에 주인
 * ID로 맡긴다 (같은 트랜잭션). 사진은 주인 것이어야 붙는다 (돕는 사람은 /owners/{ownerId}/images로 주인 경로에 올린다)
 */
@Service
public class OwnerWardrobeService implements OwnerWardrobeUseCase {

  private final ClosetAccessUseCase access;
  private final WardrobeUseCase wardrobe;

  public OwnerWardrobeService(ClosetAccessUseCase access, WardrobeUseCase wardrobe) {
    this.access = access;
    this.wardrobe = wardrobe;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> list(long helperId, ItemSearchDto search) {
    access.requireView(helperId, search.userId());
    return wardrobe.list(search);
  }

  @Override
  @Transactional(readOnly = true)
  public ItemDto get(long helperId, long ownerId, long itemId) {
    access.requireView(helperId, ownerId);
    return wardrobe.get(ownerId, itemId);
  }

  @Override
  @Transactional
  public ItemDto create(long helperId, long ownerId, CreateItemDto input) {
    ClosetAccessDto a = access.requireEdit(helperId, ownerId);
    return recorded(a, ProxyAction.ITEM_CREATE, wardrobe.create(ownerId, input));
  }

  @Override
  @Transactional
  public ItemDto patch(long helperId, long ownerId, long itemId, PatchItemDto patch) {
    ClosetAccessDto a = access.requireEdit(helperId, ownerId);
    return recorded(a, ProxyAction.ITEM_UPDATE, wardrobe.patch(ownerId, itemId, patch));
  }

  @Override
  @Transactional
  public ItemDto retire(long helperId, long ownerId, long itemId, RetireItemDto input) {
    ClosetAccessDto a = access.requireEdit(helperId, ownerId);
    return recorded(a, ProxyAction.ITEM_RETIRE, wardrobe.retire(ownerId, itemId, input));
  }

  @Override
  @Transactional
  public ItemDto restore(long helperId, long ownerId, long itemId) {
    ClosetAccessDto a = access.requireEdit(helperId, ownerId);
    boolean retired = wardrobe.get(ownerId, itemId).retiredAt() != null;
    ItemDto restored = wardrobe.restore(ownerId, itemId);
    return retired ? recorded(a, ProxyAction.ITEM_RESTORE, restored) : restored;
  }

  private ItemDto recorded(ClosetAccessDto a, ProxyAction action, ItemDto item) {
    access.record(a, action, item.id(), item.name());
    return item;
  }
}
