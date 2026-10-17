package app.wearly.code.application.port.out;

import app.wearly.code.domain.Code;
import java.util.List;
import java.util.Optional;

/** 코드 쓰기·단건 로드 (JPA) */
public interface CodePort {

    Optional<Code> find(String group, String code);

    /** 그룹의 코드 (sort_order, code_id 순서) */
    List<Code> findByGroup(String group);

    List<Code> findIn(String group, List<String> codes);

    boolean exists(String group, String code);

    long countChildren(String parentGroup, String parentCode);

    int maxSortOrder(String group);

    /** 새 코드면 넣고, 있으면 바꾼다. flush는 flush()로 */
    Code save(Code code);

    void saveAllAndFlush(List<Code> codes);

    void delete(Code code);

    void flush();
}
