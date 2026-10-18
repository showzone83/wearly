package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.image.application.port.in.dto.ItemImageRefDto;
import app.wearly.wardrobe.domain.Retirement;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 옷장 아이템 한 벌 (유스케이스 출력). 속성 값은 코드 문자열 (06 문서), ID·시각은 자바 타입 그대로. 사용 중이면 retiredAt·retireReason·
 * retireMemo가 null (13 문서). image는 대표 사진(서명 URL), 없으면 null (17 문서)
 */
public record ItemDto(
    Long id,
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
    Instant createdAt,
    Instant retiredAt,
    String retireReason,
    String retireMemo,
    ItemImageRefDto image) {

  public static ItemDto from(WardrobeItem e, ItemImageRefDto image) {
    Retirement r = e.retirement();
    return new ItemDto(
        e.id(),
        e.name(),
        e.category(),
        e.subType(),
        e.color(),
        e.pattern(),
        e.seasons(),
        e.styles(),
        e.formality(),
        e.status(),
        e.brand(),
        e.wornCount(),
        e.lastWornAt(),
        e.createdAt(),
        r == null ? null : r.at(),
        r == null ? null : r.reason(),
        r == null ? null : r.memo(),
        image);
  }
}
