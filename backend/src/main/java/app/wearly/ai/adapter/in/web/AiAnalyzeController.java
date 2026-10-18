package app.wearly.ai.adapter.in.web;

import app.wearly.ai.adapter.in.web.request.AnalyzeStoredImageRequest;
import app.wearly.ai.adapter.in.web.response.AnalyzeResponse;
import app.wearly.ai.application.port.in.AnalyzeImageUseCase;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.Uploads;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 옷 사진 분석 (05 문서 6장). 경로는 옷장 아래지만 AI 컨텍스트의 유스케이스다. 사진은 multipart 파트 image로 바로 보내거나, 서버에 저장한 사진을
 * JSON { imageId }로 가리킨다 (18 문서)
 */
@RestController
@RequestMapping("/api/v1/wardrobe")
public class AiAnalyzeController {

  private final AnalyzeImageUseCase analyze;

  public AiAnalyzeController(AnalyzeImageUseCase analyze) {
    this.analyze = analyze;
  }

  @PostMapping(path = "/items/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AnalyzeResponse analyze(
      @CurrentUser long userId,
      @RequestPart(name = "image", required = false) MultipartFile image) {
    return AnalyzeResponse.from(analyze.analyze(userId, Uploads.of(image)));
  }

  @PostMapping(path = "/items/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AnalyzeResponse analyzeStored(
      @CurrentUser long userId, @Valid @RequestBody AnalyzeStoredImageRequest request) {
    return AnalyzeResponse.from(analyze.analyzeStored(userId, request.imageId()));
  }
}
