package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.validator.constraints.Range;

/**
 * 05 문서 6장 ItemInput (ItemDto에서 id·createdAt을 뺀 등록 입력). wornCount·lastWornAt은 선택. 속성 값은 코드 문자열 (06
 * 문서)이고 코드 테이블 검증은 유스케이스가 한다. Bean Validation은 빠른 실패용이다.
 */
public record ItemRequest(
    @NotBlank @Size(max = 40) String name,
    @NotNull String category,
    @NotNull String subType,
    @NotNull String color,
    @NotNull String pattern,
    @NotNull List<@NotNull String> seasons,
    @NotNull List<@NotNull String> styles,
    @NotNull @Range(min = 1, max = 3) Integer formality,
    @NotNull String status,
    @Size(max = 40) String brand,
    @Min(0) Integer wornCount,
    LocalDate lastWornAt) {

  public CreateItemDto toDto() {
    return new CreateItemDto(
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
