package app.wearly.wardrobe;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.ItemStatus;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 계약 6장 옷장 DTO */
public final class WardrobeDtos {

    private WardrobeDtos() {
    }

    /** 앱 WardrobeItem에서 imageUri를 뺀 모양 */
    public record ItemDto(
            String id,
            String name,
            Category category,
            SubType subType,
            ColorKey color,
            Pattern pattern,
            List<Season> seasons,
            List<StyleTag> styles,
            int formality,
            ItemStatus status,
            String brand,
            int wornCount,
            LocalDate lastWornAt,
            String createdAt) {
    }

    public record ItemList(List<ItemDto> items) {
    }

    public record BulkRequest(
            @NotNull(message = "필수 값이에요.")
            @Size(max = 100, message = "한 번에 100개까지 등록할 수 있어요.")
            List<@NotNull(message = "필수 값이에요.") @Valid ItemInput> items) {
    }

    public record CountByColor(ColorKey color, long count) {
    }

    public record CountByStyle(StyleTag style, long count) {
    }

    public record WardrobeStats(
            long total,
            Map<Category, Long> byCategory,
            List<CountByColor> byColor,
            List<CountByStyle> byStyle,
            Map<ItemStatus, Long> byStatus,
            long wornLast30Days,
            double utilization30d,
            List<String> sleeping) {
    }
}
