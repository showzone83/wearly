package app.wearly.wardrobe;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.ItemStatus;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** ItemDto에서 id·createdAt을 뺀 등록 입력. wornCount·lastWornAt은 선택 (계약 6장) */
@ValidSubType
public record ItemInput(
        @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 40, message = "이름은 40자까지 쓸 수 있어요.")
        String name,
        @NotNull(message = "필수 값이에요.") Category category,
        @NotNull(message = "필수 값이에요.") SubType subType,
        @NotNull(message = "필수 값이에요.") ColorKey color,
        @NotNull(message = "필수 값이에요.") Pattern pattern,
        @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") Season> seasons,
        @NotNull(message = "필수 값이에요.") List<@NotNull(message = "허용되지 않는 값이에요.") StyleTag> styles,
        @NotNull(message = "필수 값이에요.") @Min(value = 1, message = "1~3이어야 해요.") @Max(value = 3, message = "1~3이어야 해요.")
        Integer formality,
        @NotNull(message = "필수 값이에요.") ItemStatus status,
        @Size(max = 40, message = "브랜드는 40자까지 쓸 수 있어요.") String brand,
        @Min(value = 0, message = "0 이상이어야 해요.") Integer wornCount,
        LocalDate lastWornAt) {
}
