package app.wearly.wardrobe.adapter.out.persistence.mybatis.mapper;

import app.wearly.wardrobe.adapter.out.persistence.mybatis.dto.ItemRowDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.dto.CodeCountDto;
import app.wearly.wardrobe.application.port.out.dto.KeyCountDto;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 옷장 읽기 전용 조회 (MyBatis): 목록·통계·코드 사용 수. SQL은 resources/mapper/wardrobe/WardrobeItemMapper.xml */
@Mapper
public interface WardrobeItemMapper {

  List<ItemRowDto> findItems(ItemFilter filter);

  long countAll(@Param("userId") long userId);

  List<KeyCountDto> countByCategory(@Param("userId") long userId);

  List<KeyCountDto> countByColor(@Param("userId") long userId);

  List<KeyCountDto> countByStyle(@Param("userId") long userId);

  List<KeyCountDto> countByStatus(@Param("userId") long userId);

  long countWornSince(@Param("userId") long userId, @Param("since") LocalDate since);

  List<Long> findSleepingIds(
      @Param("userId") long userId, @Param("before") LocalDate before, @Param("limit") int limit);

  /** 모든 회원의 아이템이 쓰는 코드별 개수 (코드 값을 담는 컬럼 7개) */
  List<CodeCountDto> countByCode();

  /** 이 코드를 쓰는 아이템 수 (모든 회원) */
  long countUsing(@Param("group") String group, @Param("code") String code);
}
