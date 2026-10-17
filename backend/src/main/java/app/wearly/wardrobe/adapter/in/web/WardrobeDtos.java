package app.wearly.wardrobe.adapter.in.web;

import app.wearly.wardrobe.application.port.in.ItemView;
import app.wearly.wardrobe.domain.ItemInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 05 문서 6장 옷장 요청·응답. 속성 값은 코드 문자열 (06 문서) */
public final class WardrobeDtos {

  private WardrobeDtos() {}

  /**
   * ItemDto에서 id·createdAt을 뺀 등록 입력. wornCount·lastWornAt은 선택 (05 문서 6장). 코드 테이블 검증은
   * 유스케이스(ItemCodeValidator)가 한다.
   */
  public record ItemRequest(
      @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 40, message = "이름은 40자까지 쓸 수 있어요.")
          String name,
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

    ItemInput toInput() {
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

    static ItemRequest of(ItemInput in) {
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

  public record ItemList(List<ItemView> items) {}

  public record BulkRequest(
      @NotNull(message = "필수 값이에요.") @Size(max = 100, message = "한 번에 100개까지 등록할 수 있어요.")
          List<@NotNull(message = "필수 값이에요.") @Valid ItemRequest> items) {}
}
