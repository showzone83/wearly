package app.wearly.image.application.port.out;

import app.wearly.image.application.port.out.dto.StaleImageDto;
import java.time.Instant;
import java.util.List;

/** 업로드 한도 집계·정리 대상 조회 (MyBatis) */
public interface ItemImageQueryPort {

  /** 회원별 업로드 한도 확인을 한 줄로 세운다 (트랜잭션 잠금, 끝나면 풀린다) */
  void lockUploads(long userId);

  /** since 뒤에 만든(업로드 URL을 받은) 이미지 수. 상태와 상관없이 센다 */
  long countCreatedSince(long userId, Instant since);

  /**
   * 정리 대상: before 전에 만든 PENDING, before 전에 READY가 됐는데 아이템에 안 붙은 것, before 전에 만든 DELETED. 오래된 것부터
   * limit개
   */
  List<StaleImageDto> findStale(Instant before, int limit);
}
