package app.wearly.wardrobe;

import app.wearly.common.Ids;
import app.wearly.common.Times;
import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.EnumCodes;
import app.wearly.domain.ItemStatus;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import app.wearly.wardrobe.WardrobeDtos.ItemDto;

/** 엔티티·MyBatis 행·입력 ↔ ItemDto 변환 */
final class ItemMapping {

    private ItemMapping() {
    }

    static ItemDto toDto(WardrobeItem e) {
        return new ItemDto(
                Ids.format(e.getId()),
                e.getName(),
                Category.valueOf(e.getCategory()),
                SubType.valueOf(e.getSubType()),
                ColorKey.fromCode(e.getColor()),
                Pattern.valueOf(e.getPattern()),
                EnumCodes.parse(e.getSeasons(), Season::valueOf),
                EnumCodes.parse(e.getStyles(), StyleTag::valueOf),
                e.getFormality(),
                ItemStatus.valueOf(e.getStatus()),
                e.getBrand(),
                e.getWornCount(),
                e.getLastWornAt(),
                Times.iso(e.getCreatedAt()));
    }

    static ItemDto toDto(ItemRow r) {
        return new ItemDto(
                Ids.format(r.getId()),
                r.getName(),
                Category.valueOf(r.getCategory()),
                SubType.valueOf(r.getSubType()),
                ColorKey.fromCode(r.getColor()),
                Pattern.valueOf(r.getPattern()),
                EnumCodes.parse(r.getSeasons(), Season::valueOf),
                EnumCodes.parse(r.getStyles(), StyleTag::valueOf),
                r.getFormality(),
                ItemStatus.valueOf(r.getStatus()),
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
                in.category().name(),
                in.subType().name(),
                in.color().code(),
                in.pattern().name(),
                EnumCodes.names(in.seasons()),
                EnumCodes.names(in.styles()),
                in.formality().shortValue(),
                in.status().name(),
                brand,
                in.wornCount() == null ? 0 : in.wornCount(),
                in.lastWornAt());
    }
}
