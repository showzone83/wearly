package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;

/**
 * 돕는 사람이 옷장 주인 대신 올린 사진을 AI로 분석한다 (doc/26 2단계). 등록·수정 권한을 확인하고(연결 없음 404, 권한 부족 403) 주인의 저장한 사진으로
 * 분석한다. 일일 한도는 돕는 사람 것을 쓴다 (D7). 기록하지 않는다 (데이터가 바뀌지 않음)
 */
public interface OwnerAnalyzeImageUseCase {

  AnalyzeResultDto analyzeStored(long helperId, long ownerId, String imageId);
}
