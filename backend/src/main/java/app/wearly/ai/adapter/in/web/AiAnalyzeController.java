package app.wearly.ai.adapter.in.web;

import app.wearly.ai.application.port.in.AnalyzeImageUseCase;
import app.wearly.ai.application.port.in.AnalyzeResult;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.Uploads;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 옷 사진 분석 (05 문서 6장). 경로는 옷장 아래지만 AI 컨텍스트의 유스케이스다 */
@RestController
@RequestMapping("/api/v1/wardrobe")
public class AiAnalyzeController {

  private final AnalyzeImageUseCase analyze;

  public AiAnalyzeController(AnalyzeImageUseCase analyze) {
    this.analyze = analyze;
  }

  @PostMapping(path = "/items/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AnalyzeResult analyze(
      @CurrentUser long userId,
      @RequestPart(name = "image", required = false) MultipartFile image) {
    return analyze.analyze(userId, Uploads.of(image));
  }
}
