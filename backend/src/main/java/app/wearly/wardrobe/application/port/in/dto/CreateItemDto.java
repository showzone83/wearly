package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.wardrobe.domain.ItemInput;
import java.time.LocalDate;
import java.util.List;

/** 아이템 등록 입력 (05 문서 6장 ItemInput). wornCount·lastWornAt은 선택, imageId는 붙일 사진(17 문서, 선택) */
public record CreateItemDto(
    String name,
    String category,
    String subType,
    String color,
    String pattern,
    List<String> seasons,
    List<String> styles,
    Integer formality,
    String status,
    String brand,
    Integer wornCount,
    LocalDate lastWornAt,
    String imageId) {

  public ItemInput toInput() {
    return new ItemInput(
        name,
        category,
        subType,
        color,
        pattern,
        seasons,
        styles,
        formality,
        status,
        brand,
        wornCount,
        lastWornAt);
  }
}
