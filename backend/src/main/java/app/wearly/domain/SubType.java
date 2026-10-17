package app.wearly.domain;

/** 앱 constants.ts SUBTYPE: 세부 종류와 그 카테고리 */
public enum SubType {
    TEE(Category.TOP, "티셔츠"),
    KNIT(Category.TOP, "니트"),
    SHIRT(Category.TOP, "셔츠"),
    CARDIGAN(Category.OUTER, "가디건"),
    SLACKS(Category.BOTTOM, "슬랙스"),
    WIDE(Category.BOTTOM, "와이드 팬츠"),
    CHINO(Category.BOTTOM, "치노 팬츠"),
    SKIRT(Category.BOTTOM, "스커트"),
    JACKET(Category.OUTER, "재킷"),
    COAT(Category.OUTER, "코트"),
    LOAFER(Category.SHOES, "로퍼"),
    SNEAKER(Category.SHOES, "스니커즈"),
    TOTE(Category.BAG, "토트백"),
    SHOULDER(Category.BAG, "숄더백"),
    WATCH(Category.ACC, "시계");

    private final Category category;
    private final String label;

    SubType(Category category, String label) {
        this.category = category;
        this.label = label;
    }

    public Category category() {
        return category;
    }

    public String label() {
        return label;
    }

    public boolean belongsTo(Category category) {
        return this.category == category;
    }
}
