package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.wardrobe.domain.ItemInput;
import java.time.LocalDate;
import java.util.List;

/**
 * 아이템 등록 입력 (05 문서 6장 ItemInput). wornCount·lastWornAt·purchasedAt은 선택, imageId는 붙일 사진(18 문서, 선택).
 * 색은 colors(1~3개, 첫 번째가 주 색상) 또는 예전 color(하나) — 둘 다 오면 colors (이슈 #4)
 */
public record CreateItemDto(
    String name,
    String category,
    String subType,
    String color,
    List<String> colors,
    String pattern,
    List<String> seasons,
    List<String> styles,
    Integer formality,
    String status,
    String brand,
    Integer wornCount,
    LocalDate lastWornAt,
    LocalDate purchasedAt,
    String imageId) {

  public ItemInput toInput() {
    return new ItemInput(
        name,
        category,
        subType,
        ItemInput.colorsOf(color, colors),
        ItemInput.isLegacy(color, colors),
        pattern,
        seasons,
        styles,
        formality,
        status,
        brand,
        wornCount,
        lastWornAt,
        purchasedAt);
  }
}
