package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AnalyzeImageUseCase;
import app.wearly.ai.application.port.in.OwnerAnalyzeImageUseCase;
import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;
import app.wearly.pairing.application.port.in.ClosetAccessUseCase;
import org.springframework.stereotype.Service;

/** 옷장 도우미의 사진 분석 (doc/26 2단계): 권한 확인 뒤 주인 사진·돕는 사람 한도로 분석. AI 호출은 트랜잭션 밖에서 한다 */
@Service
public class OwnerAnalyzeImageService implements OwnerAnalyzeImageUseCase {

  private final ClosetAccessUseCase access;
  private final AnalyzeImageUseCase analyze;

  public OwnerAnalyzeImageService(ClosetAccessUseCase access, AnalyzeImageUseCase analyze) {
    this.access = access;
    this.analyze = analyze;
  }

  @Override
  public AnalyzeResultDto analyzeStored(long helperId, long ownerId, String imageId) {
    access.requireEdit(helperId, ownerId);
    return analyze.analyzeStored(helperId, ownerId, imageId);
  }
}
