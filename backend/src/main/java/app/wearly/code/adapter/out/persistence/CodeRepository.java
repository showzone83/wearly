package app.wearly.code.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeRepository extends JpaRepository<CodeEntity, Long> {

  Optional<CodeEntity> findByGroupCodeAndCode(String groupCode, String code);

  List<CodeEntity> findByGroupCodeOrderBySortOrderAscIdAsc(String groupCode);

  List<CodeEntity> findByGroupCodeAndCodeIn(String groupCode, List<String> codes);

  boolean existsByGroupCodeAndCode(String groupCode, String code);

  long countByGroupCodeAndParentCode(String groupCode, String parentCode);

  @Query("select coalesce(max(c.sortOrder), 0) from CodeEntity c where c.groupCode = :group")
  int maxSortOrder(@Param("group") String group);
}
