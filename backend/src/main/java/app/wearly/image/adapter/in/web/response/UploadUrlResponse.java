package app.wearly.image.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.image.application.port.in.dto.UploadUrlDto;
import java.util.Map;

/** { imageId, uploadUrl, method: 'PUT', headers: { 'Content-Type': ... }, expiresAt } */
public record UploadUrlResponse(
    String imageId,
    String uploadUrl,
    String method,
    Map<String, String> headers,
    String expiresAt) {

  public static UploadUrlResponse from(UploadUrlDto d) {
    return new UploadUrlResponse(
        d.imageId().toString(), d.uploadUrl(), d.method(), d.headers(), Times.iso(d.expiresAt()));
  }
}
