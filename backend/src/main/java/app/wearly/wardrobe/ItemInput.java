package app.wearly.wardrobe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * ItemDto에서 id·createdAt을 뺀 등록 입력. wornCount·lastWornAt은 선택 (계약 05 6장).
 * 종류·세부 종류·색·패턴·계절·스타일·보유 상태는 코드 문자열이고, 값 검증은 ItemCodeValidator가 코드 테이블로 한다.
 */
public record ItemInput(
        @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 40, message = "이름은 40자까지 쓸 수 있어요.")
        String name,
        @NotBlank(message = "필수 값이에요.") String category,
        @NotBlank(message = "필수 값이에요.") String subType,
        @NotBlank(message = "필수 값이에요.") String color,
        @NotBlank(message = "필수 값이에요.") String pattern,
        @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") String> seasons,
        @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") String> styles,
        @NotNull(message = "필수 값이에요.") @Min(value = 1, message = "1~3이어야 해요.") @Max(value = 3, message = "1~3이어야 해요.")
        Integer formality,
        @NotBlank(message = "필수 값이에요.") String status,
        @Size(max = 40, message = "브랜드는 40자까지 쓸 수 있어요.") String brand,
        @Min(value = 0, message = "0 이상이어야 해요.") Integer wornCount,
        LocalDate lastWornAt) {
}
