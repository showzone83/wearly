package app.wearly.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 아이템 등록 본문 도우미 */
public final class TestItems {

    private TestItems() {
    }

    public static Map<String, Object> item(String name, String category, String subType, String color) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("subType", subType);
        m.put("color", color);
        m.put("pattern", "SOLID");
        m.put("seasons", List.of("SPRING", "FALL"));
        m.put("styles", List.of("NEAT"));
        m.put("formality", 2);
        m.put("status", "OWNED");
        return m;
    }
}
