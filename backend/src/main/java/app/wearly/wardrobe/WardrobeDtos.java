package app.wearly.wardrobe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 05 문서 6장 옷장 DTO. 속성 값은 코드 문자열 (06 문서) */
public final class WardrobeDtos {

    private WardrobeDtos() {
    }

    /** 앱 WardrobeItem에서 imageUri를 뺀 모양 */
    public record ItemDto(
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
    }

    public record ItemList(List<ItemDto> items) {
    }

    public record BulkRequest(
            @NotNull(message = "필수 값이에요.")
            @Size(max = 100, message = "한 번에 100개까지 등록할 수 있어요.")
            List<@NotNull(message = "필수 값이에요.") @Valid ItemInput> items) {
    }

    public record CountByColor(String color, long count) {
    }

    public record CountByStyle(String style, long count) {
    }

    /** byCategory·byStatus의 키는 활성 코드 전부(0 포함) + 아이템이 있는 비활성 코드, 코드 순서대로 */
    public record WardrobeStats(
            long total,
            Map<String, Long> byCategory,
            List<CountByColor> byColor,
            List<CountByStyle> byStyle,
            Map<String, Long> byStatus,
            long wornLast30Days,
            double utilization30d,
            List<String> sleeping) {
    }
}
