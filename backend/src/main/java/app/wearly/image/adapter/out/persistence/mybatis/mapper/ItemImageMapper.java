package app.wearly.image.adapter.out.persistence.mybatis.mapper;

import app.wearly.image.application.port.out.dto.StaleImageDto;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사진 업로드 한도 집계·정리 대상 조회 (MyBatis). SQL은 resources/mapper/image/ItemImageMapper.xml */
@Mapper
public interface ItemImageMapper {

  /** 회원별 업로드 한도 확인 잠금 (pg_advisory_xact_lock). 트랜잭션이 끝나면 풀린다 */
  int lockUploads(@Param("userId") long userId);

  long countCreatedSince(@Param("userId") long userId, @Param("since") Instant since);

  List<StaleImageDto> findStale(@Param("before") Instant before, @Param("limit") int limit);
}
