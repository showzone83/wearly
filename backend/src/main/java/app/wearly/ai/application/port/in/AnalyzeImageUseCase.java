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
}
