package app.wearly.wardrobe.application.port.out;

import app.wearly.wardrobe.application.port.out.dto.CodeCountDto;
import app.wearly.wardrobe.application.port.out.dto.KeyCountDto;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.LocalDate;
import java.util.List;

/** 옷장 목록·통계·코드 사용 수 조회 (MyBatis, 읽기 전용) */
public interface ItemQueryPort {

  List<WardrobeItem> findItems(ItemFilter filter);

  long countAll(long userId);

  List<KeyCountDto> countByCategory(long userId);

  /** 많은 순, 같으면 색 코드 순 */
  List<KeyCountDto> countByColor(long userId);

  /** 많은 순, 같으면 스타일 코드 순 */
  List<KeyCountDto> countByStyle(long userId);

  List<KeyCountDto> countByStatus(long userId);

  long countWornSince(long userId, LocalDate since);

  /** 한 번도 안 입었거나 기준일 전에 마지막으로 입은 아이템. 오래 잠든 것부터 */
  List<Long> findSleepingIds(long userId, LocalDate before, int limit);

  /** 모든 회원의 아이템이 쓰는 코드별 개수 (코드 값을 담는 컬럼 7개 전부) */
  List<CodeCountDto> countByCode();

  /** 이 코드를 쓰는 아이템 수 (모든 회원). 아이템이 쓰지 않는 그룹이면 0 */
  long countUsing(String group, String code);
}
