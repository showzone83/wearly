package app.wearly.ai.application.port.in;

import java.util.List;

/** 지원 공급자 종류와 options 설명 (GET /admin/ai/types) */
public record ProviderTypeInfo(String type, String description, List<Option> options) {

    /** min·max는 숫자 칸, values는 enum 칸에만 있다 (없으면 null) */
    public record Option(String name, String kind, String description, Object defaultValue, Number min, Number max,
            List<String> values) {
    }
}
