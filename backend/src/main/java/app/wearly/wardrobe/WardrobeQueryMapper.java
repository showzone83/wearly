package app.wearly.wardrobe;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 옷장 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/WardrobeQueryMapper.xml */
@Mapper
public interface WardrobeQueryMapper {

    List<ItemRow> findItems(ItemFilter filter);

    long countAll(@Param("userId") long userId);

    List<CountRow> countByCategory(@Param("userId") long userId);

    List<CountRow> countByColor(@Param("userId") long userId);

    List<CountRow> countByStyle(@Param("userId") long userId);

    List<CountRow> countByStatus(@Param("userId") long userId);

    long countWornSince(@Param("userId") long userId, @Param("since") LocalDate since);

    List<Long> findSleepingIds(@Param("userId") long userId, @Param("before") LocalDate before, @Param("limit") int limit);
}
