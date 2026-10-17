package app.wearly.wardrobe.adapter.out.persistence.mybatis.mapper;

import app.wearly.wardrobe.adapter.out.persistence.mybatis.dto.CountRowDto;
import app.wearly.wardrobe.adapter.out.persistence.mybatis.dto.ItemRowDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 옷장 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/wardrobe/WardrobeItemMapper.xml */
@Mapper
public interface WardrobeItemMapper {

  List<ItemRowDto> findItems(ItemFilter filter);

  long countAll(@Param("userId") long userId);

  List<CountRowDto> countByCategory(@Param("userId") long userId);

  List<CountRowDto> countByColor(@Param("userId") long userId);

  List<CountRowDto> countByStyle(@Param("userId") long userId);

  List<CountRowDto> countByStatus(@Param("userId") long userId);

  long countWornSince(@Param("userId") long userId, @Param("since") LocalDate since);

  List<Long> findSleepingIds(
      @Param("userId") long userId, @Param("before") LocalDate before, @Param("limit") int limit);
}
