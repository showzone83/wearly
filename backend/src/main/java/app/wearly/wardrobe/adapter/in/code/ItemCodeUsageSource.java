package app.wearly.wardrobe.adapter.in.code;

import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.wardrobe.application.port.in.ItemCodeUsageUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * code 컨텍스트가 정의한 코드 사용처(CodeUsageSource)를 옷장 아이템으로 구현한다 (H1). code가 이 어댑터를 통해 옷장 유스케이스를 부르는 입력
 * 어댑터라서, 의존은 wardrobe → code 한 방향이다.
 */
@Component
class ItemCodeUsageSource implements CodeUsageSource {

  private final ItemCodeUsageUseCase usage;

  ItemCodeUsageSource(ItemCodeUsageUseCase usage) {
    this.usage = usage;
  }

  @Override
  public Kind kind() {
    return Kind.ITEM;
  }

  @Override
  public List<CodeUsageCountDto> countByCode() {
    return usage.countByCode().stream()
        .map(c -> new CodeUsageCountDto(c.group(), c.code(), c.count()))
        .toList();
  }

  @Override
  public long countUsing(String group, String code) {
    return usage.countItemsUsing(group, code);
  }
}
