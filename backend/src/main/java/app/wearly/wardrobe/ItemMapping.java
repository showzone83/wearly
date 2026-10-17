package app.wearly.wardrobe;

import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.wardrobe.WardrobeDtos.ItemDto;
import java.util.List;

/** 엔티티·MyBatis 행·입력 ↔ ItemDto 변환 */
final class ItemMapping {

    private ItemMapping() {
    }

    static ItemDto toDto(WardrobeItem e) {
        return new ItemDto(
                Ids.format(e.getId()),
                e.getName(),
                e.getCategory(),
                e.getSubType(),
                e.getColor(),
                e.getPattern(),
                copy(e.getSeasons()),
                copy(e.getStyles()),
                e.getFormality(),
                e.getStatus(),
                e.getBrand(),
                e.getWornCount(),
                e.getLastWornAt(),
                Times.iso(e.getCreatedAt()));
    }

    static ItemDto toDto(ItemRow r) {
        return new ItemDto(
                Ids.format(r.getId()),
                r.getName(),
                r.getCategory(),
                r.getSubType(),
                r.getColor(),
                r.getPattern(),
                copy(r.getSeasons()),
                copy(r.getStyles()),
                r.getFormality(),
                r.getStatus(),
                r.getBrand(),
                r.getWornCount(),
                r.getLastWornAt(),
                Times.iso(r.getCreatedAt()));
    }

    /** PATCH 병합의 출발점: 저장된 값을 입력 모양으로 */
    static ItemInput toInput(WardrobeItem e) {
        ItemDto d = toDto(e);
        return new ItemInput(d.name(), d.category(), d.subType(), d.color(), d.pattern(), d.seasons(), d.styles(),
                d.formality(), d.status(), d.brand(), d.wornCount(), d.lastWornAt());
    }

    static void apply(WardrobeItem e, ItemInput in) {
        String brand = in.brand() == null || in.brand().isBlank() ? null : in.brand().strip();
        e.apply(
                in.name().strip(),
                in.category(),
                in.subType(),
                in.color(),
                in.pattern(),
                distinct(in.seasons()),
                distinct(in.styles()),
                in.formality().shortValue(),
                in.status(),
                brand,
                in.wornCount() == null ? 0 : in.wornCount(),
                in.lastWornAt());
    }

    /** 중복을 없애고 순서는 지킨다 */
    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
