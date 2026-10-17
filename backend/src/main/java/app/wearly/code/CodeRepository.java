package app.wearly.code;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeRepository extends JpaRepository<Code, Long> {

    Optional<Code> findByGroupCodeAndCode(String groupCode, String code);

    boolean existsByGroupCodeAndCode(String groupCode, String code);

    List<Code> findByGroupCodeOrderBySortOrderAscIdAsc(String groupCode);

    boolean existsByGroupCodeAndParentCode(String groupCode, String parentCode);

    @Query("select coalesce(max(c.sortOrder), 0) from Code c where c.groupCode = :group")
    int maxSortOrder(@Param("group") String group);
}
