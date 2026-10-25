package app.wearly.ai.adapter.in.web;

import app.wearly.ai.adapter.in.web.request.AnalyzeStoredImageRequest;
import app.wearly.ai.adapter.in.web.response.AnalyzeResponse;
import app.wearly.ai.application.port.in.OwnerAnalyzeImageUseCase;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옷장 도우미가 주인 대신 올린 사진을 분석한다 (26 문서 4.6). 주인 경로에 저장한 사진만 JSON { imageId }로 받는다 (multipart 바로 보내기는
 * 없다). 응답은 /wardrobe/items/analyze와 같고 remaining은 돕는 사람의 남은 횟수다
 */
@RestController
@RequestMapping("/api/v1/owners/{ownerId}/wardrobe")
public class OwnerAiAnalyzeController {

  private final OwnerAnalyzeImageUseCase analyze;

  public OwnerAiAnalyzeController(OwnerAnalyzeImageUseCase analyze) {
    this.analyze = analyze;
  }

  @PostMapping(path = "/items/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AnalyzeResponse analyzeStored(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @Valid @RequestBody AnalyzeStoredImageRequest request) {
    return AnalyzeResponse.from(
        analyze.analyzeStored(userId, PathIds.parse(ownerId), request.imageId()));
  }
}
