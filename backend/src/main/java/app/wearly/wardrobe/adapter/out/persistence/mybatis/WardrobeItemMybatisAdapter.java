package app.wearly.wardrobe.adapter.out.persistence.mybatis;

import app.wearly.wardrobe.adapter.out.persistence.mybatis.dto.ItemRowDto;
import app.wearly.wardrobe.adapter.out.persistence.mybatis.mapper.WardrobeItemMapper;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.application.port.out.dto.CodeCountDto;
import app.wearly.wardrobe.application.port.out.dto.KeyCountDto;
import app.wearly.wardrobe.domain.Retirement;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** ItemQueryPort 구현 (MyBatis). 목록 행 → 도메인 모델 변환, 집계는 매퍼가 port.out.dto로 바로 돌려준다 */
@Component
class WardrobeItemMybatisAdapter implements ItemQueryPort {

  private final WardrobeItemMapper mapper;

  WardrobeItemMybatisAdapter(WardrobeItemMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<WardrobeItem> findItems(ItemFilter filter) {
    return mapper.findItems(filter).stream().map(r -> toDomain(r, filter.userId())).toList();
  }

  @Override
  public long countAll(long userId) {
    return mapper.countAll(userId);
  }

  @Override
  public List<KeyCountDto> countByCategory(long userId) {
    return mapper.countByCategory(userId);
  }

  @Override
  public List<KeyCountDto> countByColor(long userId) {
    return mapper.countByColor(userId);
  }

  @Override
  public List<KeyCountDto> countByStyle(long userId) {
    return mapper.countByStyle(userId);
  }

  @Override
  public List<KeyCountDto> countByStatus(long userId) {
    return mapper.countByStatus(userId);
  }

  @Override
  public long countWornSince(long userId, LocalDate since) {
    return mapper.countWornSince(userId, since);
  }

  @Override
  public List<Long> findSleepingIds(long userId, LocalDate before, int limit) {
    return mapper.findSleepingIds(userId, before, limit);
  }

  @Override
  public long countRetired(long userId) {
    return mapper.countRetired(userId);
  }

  @Override
  public List<KeyCountDto> countRetiredByReason(long userId) {
    return mapper.countRetiredByReason(userId);
  }

  @Override
  public List<CodeCountDto> countByCode() {
    return mapper.countByCode();
  }

  @Override
  public long countUsing(String group, String code) {
    return mapper.countUsing(group, code);
  }

  private static WardrobeItem toDomain(ItemRowDto r, long userId) {
    return new WardrobeItem(
        r.getId(),
        userId,
        r.getName(),
        r.getCategory(),
        r.getSubType(),
        r.getColor(),
        r.getPattern(),
        r.getSeasons(),
        r.getStyles(),
        r.getFormality(),
        r.getStatus(),
        r.getBrand(),
        r.getWornCount(),
        r.getLastWornAt(),
        r.getCreatedAt(),
        r.getRetiredAt() == null
            ? null
            : new Retirement(r.getRetiredAt(), r.getRetireReason(), r.getRetireMemo()));
  }
}
