package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 옷장 아이템 한 벌 (유스케이스 출력). 속성 값은 코드 문자열 (06 문서), ID·시각은 자바 타입 그대로 */
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
    Instant createdAt) {

  public static ItemDto from(WardrobeItem e) {
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
        e.createdAt());
  }
}
