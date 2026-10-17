package app.wearly.code;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 코드 테이블 읽기 (MyBatis). SQL은 resources/mapper/CodeQueryMapper.xml */
@Mapper
public interface CodeQueryMapper {

    /** 그룹 순서대로 */
    List<CodeGroupRow> findGroups();

    /** 그룹 안에서 sort_order 순서대로 (비활성 포함) */
    List<CodeRow> findCodes();
}
