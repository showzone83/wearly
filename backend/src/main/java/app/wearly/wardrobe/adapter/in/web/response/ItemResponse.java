package app.wearly.wardrobe.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import java.time.LocalDate;
import java.util.List;

/**
 * 05 문서 6장 ItemDto (앱 WardrobeItem에서 imageUri를 뺀 모양). ID는 문자열, createdAt·retiredAt은 ISO-8601 UTC.
 * 사용 중이면 retiredAt·retireReason·retireMemo가 null (13 문서 3장). image는 서버에 저장한 대표 사진, 없으면 null (18 문서)
 */
public record ItemResponse(
    String id,
    String name,
    String category,
    String subType,
    String color,
    String pattern,
    List<String> seasons,
    List<String> styles,
    int formality,
    String status,
    String brand,
    int wornCount,
    LocalDate lastWornAt,
    String createdAt,
    String retiredAt,
    String retireReason,
    String retireMemo,
    ItemImageResponse image) {

  public static ItemResponse from(ItemDto d) {
    return new ItemResponse(
        Ids.format(d.id()),
        d.name(),
        d.category(),
        d.subType(),
        d.color(),
        d.pattern(),
        d.seasons(),
        d.styles(),
        d.formality(),
        d.status(),
        d.brand(),
        d.wornCount(),
        d.lastWornAt(),
        Times.iso(d.createdAt()),
        Times.iso(d.retiredAt()),
        d.retireReason(),
        d.retireMemo(),
        ItemImageResponse.from(d.image()));
  }
}
