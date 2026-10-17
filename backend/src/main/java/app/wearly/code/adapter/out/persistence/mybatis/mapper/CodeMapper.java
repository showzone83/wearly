package app.wearly.code.adapter.out.persistence.mybatis.mapper;

import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeGroupRowDto;
import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeRowDto;
import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeUsageRowDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 코드 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/code/CodeMapper.xml */
@Mapper
public interface CodeMapper {

  List<CodeGroupRowDto> findGroups();

  /** 전체 코드. 그룹 순서와 상관없이 (group_code, sort_order, code_id) 순서 */
  List<CodeRowDto> findCodes();

  /** 코드 테이블 버전: max(updated_at) 밀리초 ISO + '#' + 개수 */
  String version();

  /** 아이템이 쓰는 코드별 개수 (그룹 7개 전부) */
  List<CodeUsageRowDto> countItemUsage();

  /** 이 코드를 쓰는 아이템 수 */
  long countItemsUsing(@Param("group") String group, @Param("code") String code);

  /** 이 코드를 쓰는 프로필 수 (COLOR → colors, STYLE → moods. 그 밖은 0) */
  long countProfilesUsing(@Param("group") String group, @Param("code") String code);
}
