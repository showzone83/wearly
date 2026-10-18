package app.wearly.image.application.port.in;

import app.wearly.image.application.port.in.dto.CleanupResultDto;

/** 사진 정리 (18 문서 3장) */
public interface ImageCleanupUseCase {

  /**
   * 오래된(stale-after, 기본 1일) PENDING, 아이템에 안 붙은 READY, DELETED 이미지의 객체와 행을 지운다. 주기 작업이 부르고 테스트도 직접
   * 부른다
   */
  CleanupResultDto cleanup();

  /** 회원 탈퇴: 커밋 뒤 users/{userId}/·uploads/{userId}/ 아래 객체를 모두 지운다 (행은 FK CASCADE로 지워진다) */
  void deleteAllOf(long userId);
}
