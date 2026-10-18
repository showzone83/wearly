package app.wearly.image.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.image.adapter.in.web.request.UploadUrlRequest;
import app.wearly.image.adapter.in.web.response.ImageResponse;
import app.wearly.image.adapter.in.web.response.UploadUrlResponse;
import app.wearly.image.application.port.in.ImageUploadUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 옷 사진 올리기 (17 문서 3장). 모두 로그인 필요, 남의 이미지는 404 */
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

  private final ImageUploadUseCase uploads;

  public ImageController(ImageUploadUseCase uploads) {
    this.uploads = uploads;
  }

  /** PUT 서명 URL 발급. 앱은 받은 headers를 그대로 붙여 uploadUrl로 바로 올린다 */
  @PostMapping("/upload-url")
  public UploadUrlResponse uploadUrl(
      @CurrentUser long userId, @RequestBody UploadUrlRequest request) {
    return UploadUrlResponse.from(uploads.createUploadUrl(userId, request.toDto()));
  }

  /** 올린 사진 검증·메타데이터 제거·축소 → READY. 이미 READY면 같은 응답(새 서명 URL) */
  @PostMapping("/{imageId}/complete")
  public ImageResponse complete(@CurrentUser long userId, @PathVariable String imageId) {
    return ImageResponse.from(uploads.complete(userId, imageId));
  }
}
