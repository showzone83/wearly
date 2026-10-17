package app.wearly.ai.domain;

import java.util.List;

/** 공급자 options 한 항목의 설명 (GET /admin/ai/types, 관리자 화면 폼용). 값이 없는 항목은 응답에서 빠진다 */
public record OptionSpec(String name, String kind, String description, Object defaultValue, Number min, Number max,
        List<String> values) {

    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String NUMBER = "number";
    public static final String ENUM = "enum";
    public static final String URL = "url";

    static OptionSpec integer(String name, String description, Integer def, int min, int max) {
        return new OptionSpec(name, INTEGER, description, def, min, max, null);
    }

    static OptionSpec number(String name, String description, Double def, double min, double max) {
        return new OptionSpec(name, NUMBER, description, def, min, max, null);
    }

    static OptionSpec oneOf(String name, String description, String def, List<String> values) {
        return new OptionSpec(name, ENUM, description, def, null, null, values);
    }

    static OptionSpec url(String name, String description, String def) {
        return new OptionSpec(name, URL, description, def, null, null, null);
    }
}
