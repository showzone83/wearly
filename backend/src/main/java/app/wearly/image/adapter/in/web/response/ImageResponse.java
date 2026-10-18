package app.wearly.image.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.image.application.port.in.dto.ImageDto;

/** complete 응답: { imageId, url, thumbUrl, width, height, expiresAt }. url·thumbUrl은 GET 서명 URL */
public record ImageResponse(
    String imageId, String url, String thumbUrl, int width, int height, String expiresAt) {

  public static ImageResponse from(ImageDto d) {
    return new ImageResponse(
        d.imageId().toString(),
        d.url(),
        d.thumbUrl(),
        d.width(),
        d.height(),
        Times.iso(d.expiresAt()));
  }
}
