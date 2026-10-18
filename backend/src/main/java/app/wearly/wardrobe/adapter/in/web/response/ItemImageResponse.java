package app.wearly.wardrobe.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.image.application.port.in.dto.ItemImageRefDto;

/**
 * 아이템의 대표 사진 (18 문서 3장): { imageId, url, thumbUrl, expiresAt }. url(original)·thumbUrl은
 * download-url-ttl(기본 1시간) 동안 유효한 GET 서명 URL
 */
public record ItemImageResponse(String imageId, String url, String thumbUrl, String expiresAt) {

  public static ItemImageResponse from(ItemImageRefDto d) {
    return d == null
        ? null
        : new ItemImageResponse(
            d.imageId().toString(), d.url(), d.thumbUrl(), Times.iso(d.expiresAt()));
  }
}
