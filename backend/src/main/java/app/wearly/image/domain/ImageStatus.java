package app.wearly.image.domain;

/** 이미지 상태 (18 문서 2장) */
public enum ImageStatus {
  /** 업로드 URL을 받았고 아직 complete 전 */
  PENDING,
  /** 검증·다시 인코딩을 마쳐 original·thumb가 저장됨 */
  READY,
  /** 사진 교체·아이템 삭제·처리 실패로 버림 (객체는 지우고 행은 정리 작업이 지운다) */
  DELETED
}
