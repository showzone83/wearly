package app.wearly.wardrobe.application.service;

import app.wearly.wardrobe.application.port.in.ItemCodeUsageUseCase;
import app.wearly.wardrobe.application.port.in.dto.ItemCodeCountDto;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옷장 아이템의 코드 사용 수 (MyBatis). code 컨텍스트가 CodeUsageSource로 묻는다 (H1) */
@Service
public class ItemCodeUsageService implements ItemCodeUsageUseCase {

  private final ItemQueryPort queries;

  public ItemCodeUsageService(ItemQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemCodeCountDto> countByCode() {
    return queries.countByCode().stream()
        .map(c -> new ItemCodeCountDto(c.groupCode(), c.code(), c.count()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countItemsUsing(String group, String code) {
    return queries.countUsing(group, code);
  }
}
