package app.wearly.code.adapter.out.persistence.mybatis.mapper;

import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeGroupRowDto;
import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeRowDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 코드 테이블 읽기 (MyBatis): 캐시 적재와 버전. 다른 컨텍스트의 테이블은 읽지 않는다 (H1). SQL은
 * resources/mapper/code/CodeMapper.xml
 */
@Mapper
public interface CodeMapper {

  List<CodeGroupRowDto> findGroups();

  /** 전체 코드. 그룹 순서와 상관없이 (group_code, sort_order, code_id) 순서 */
  List<CodeRowDto> findCodes();

  /** 코드 테이블 버전: max(updated_at) 밀리초 ISO + '#' + 개수 */
  String version();
}
