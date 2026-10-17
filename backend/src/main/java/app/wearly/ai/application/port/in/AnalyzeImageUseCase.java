package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;
import app.wearly.common.application.ImageUpload;

/** 사진 분석 (05 문서 6장 POST /wardrobe/items/analyze): 형식·크기 검사 → 일일 한도 → 공급자를 우선순위 순으로 시도 → 기록. */
public interface AnalyzeImageUseCase {

  AnalyzeResultDto analyze(long userId, ImageUpload image);
}
