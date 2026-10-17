package app.wearly.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 앱 types.ts ColorKey. JSON과 DB에는 소문자 코드(navy 등)로 저장한다. */
public enum ColorKey {
    NAVY("navy", "네이비"),
    BLACK("black", "블랙"),
    CHARCOAL("charcoal", "차콜"),
    GRAY("gray", "그레이"),
    WHITE("white", "화이트"),
    IVORY("ivory", "아이보리"),
    BEIGE("beige", "베이지"),
    CAMEL("camel", "카멜"),
    BROWN("brown", "브라운"),
    KHAKI("khaki", "카키"),
    LIGHTBLUE("lightblue", "라이트블루"),
    BURGUNDY("burgundy", "버건디");

    private final String code;
    private final String label;

    ColorKey(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    @JsonCreator
    public static ColorKey fromCode(String code) {
        for (ColorKey c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 색: " + code);
    }
}
