package app.wearly.image.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.image.adapter.in.web.request.UploadUrlRequest;
import app.wearly.image.adapter.in.web.response.ImageResponse;
import app.wearly.image.adapter.in.web.response.UploadUrlResponse;
import app.wearly.image.application.port.in.OwnerImageUploadUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 옷장 도우미가 주인 대신 옷 사진을 올린다 (26 문서 4.6). 요청·응답은 /images와 같다. 연결이 없으면 404, 보기 권한뿐이면 403 */
@RestController
@RequestMapping("/api/v1/owners/{ownerId}/images")
public class OwnerImageController {

  private final OwnerImageUploadUseCase uploads;

  public OwnerImageController(OwnerImageUploadUseCase uploads) {
    this.uploads = uploads;
  }

  @PostMapping("/upload-url")
  public UploadUrlResponse uploadUrl(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @RequestBody UploadUrlRequest request) {
    return UploadUrlResponse.from(
        uploads.createUploadUrl(userId, PathIds.parse(ownerId), request.toDto()));
  }

  @PostMapping("/{imageId}/complete")
  public ImageResponse complete(
      @CurrentUser long userId, @PathVariable String ownerId, @PathVariable String imageId) {
    return ImageResponse.from(uploads.complete(userId, PathIds.parse(ownerId), imageId));
  }
}
