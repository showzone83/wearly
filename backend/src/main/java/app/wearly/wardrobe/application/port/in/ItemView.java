package app.wearly.wardrobe.application.port.in;

import app.wearly.common.domain.Ids;
import app.wearly.common.domain.Times;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.LocalDate;
import java.util.List;

/** 05 문서 6장 ItemDto (앱 WardrobeItem에서 imageUri를 뺀 모양). 속성 값은 코드 문자열 (06 문서) */
public record ItemView(
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
    String createdAt) {

  public static ItemView of(WardrobeItem e) {
    return new ItemView(
        Ids.format(e.id()),
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
        Times.iso(e.createdAt()));
  }
}
