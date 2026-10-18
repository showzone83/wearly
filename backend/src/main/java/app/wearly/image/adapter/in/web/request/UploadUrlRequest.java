package app.wearly.image.adapter.in.web.request;

import app.wearly.image.application.port.in.dto.CreateUploadUrlDto;

/**
 * POST /images/upload-url 본문 (17 문서 3장). 값 검증(purpose ITEM, contentType image/jpeg·image/png, size
 * 1~최대)은 유스케이스가 한다 (최대 크기가 설정 값이라서).
 */
public record UploadUrlRequest(String purpose, String contentType, Long size) {

  public CreateUploadUrlDto toDto() {
    return new CreateUploadUrlDto(purpose, contentType, size);
  }
}
