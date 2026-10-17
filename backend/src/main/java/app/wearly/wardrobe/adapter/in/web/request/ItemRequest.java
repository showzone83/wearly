package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.domain.ItemInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 05 문서 6장 ItemInput (ItemDto에서 id·createdAt을 뺀 등록 입력). wornCount·lastWornAt은 선택. 속성 값은 코드 문자열 (06
 * 문서)이고 코드 테이블 검증은 유스케이스가 한다. Bean Validation은 빠른 실패용이다.
 */
public record ItemRequest(
    @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 40, message = "이름은 40자까지 쓸 수 있어요.") String name,
    @NotNull(message = "필수 값이에요.") String category,
    @NotNull(message = "필수 값이에요.") String subType,
    @NotNull(message = "필수 값이에요.") String color,
    @NotNull(message = "필수 값이에요.") String pattern,
    @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") String> seasons,
    @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") String> styles,
    @NotNull(message = "필수 값이에요.")
        @Min(value = 1, message = "1~3이어야 해요.")
        @Max(value = 3, message = "1~3이어야 해요.")
        Integer formality,
    @NotNull(message = "필수 값이에요.") String status,
    @Size(max = 40, message = "브랜드는 40자까지 쓸 수 있어요.") String brand,
    @Min(value = 0, message = "0 이상이어야 해요.") Integer wornCount,
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

  /** PATCH 병합 결과 → 유스케이스 입력 값 */
  public ItemInput toInput() {
    return toDto().toInput();
  }

  /** PATCH 병합의 출발점: 저장된 값 */
  public static ItemRequest of(ItemInput in) {
    return new ItemRequest(
        in.name(),
        in.category(),
        in.subType(),
        in.color(),
        in.pattern(),
        in.seasons(),
        in.styles(),
        in.formality(),
        in.status(),
        in.brand(),
        in.wornCount(),
        in.lastWornAt());
  }
}
