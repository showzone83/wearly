package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;
import app.wearly.common.application.ImageUpload;

/** 사진 분석 (05 문서 6장 POST /wardrobe/items/analyze): 형식·크기 검사 → 일일 한도 → 공급자를 우선순위 순으로 시도 → 기록. */
public interface AnalyzeImageUseCase {

  AnalyzeResultDto analyze(long userId, ImageUpload image);

  /**
   * 서버에 저장한 내 사진(18 문서, READY)의 original로 분석한다. 없거나 남의 것은 404, PENDING이면 400 IMAGE_NOT_UPLOADED.
   * 그다음은 analyze와 같다
   */
  AnalyzeResultDto analyzeStored(long userId, String imageId);

  /**
   * imageOwnerId의 저장한 사진으로 분석하고 일일 한도·기록은 quotaUserId로 센다 (옷장 도우미가 주인 사진을 분석할 때 돕는 사람 한도를 쓴다,
   * doc/26 D7). 권한 확인은 부르는 쪽이 한다
   */
  AnalyzeResultDto analyzeStored(long quotaUserId, long imageOwnerId, String imageId);
}
