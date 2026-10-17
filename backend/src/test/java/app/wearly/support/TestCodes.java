package app.wearly.support;

import app.wearly.code.CodeSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 단위 테스트용 코드: 계약 원본 doc/06_코드_초기값.json을 그대로 읽어 CodeSnapshot으로 만든다. */
public final class TestCodes {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static CodeSnapshot seed;

    private TestCodes() {
    }

    public static synchronized CodeSnapshot seed() {
        if (seed == null) {
            seed = load(Path.of("..", "doc", "06_코드_초기값.json"));
        }
        return seed;
    }

    static CodeSnapshot load(Path path) {
        try {
            JsonNode root = JSON.readTree(Files.readString(path));
            List<CodeSnapshot.Group> groups = new ArrayList<>();
            int gi = 0;
            for (JsonNode g : root.get("groups")) {
                gi++;
                List<CodeSnapshot.Entry> codes = new ArrayList<>();
                for (JsonNode c : g.get("codes")) {
                    Map<String, Object> attrs = JSON.convertValue(c.get("attrs"), new TypeReference<>() {
                    });
                    codes.add(new CodeSnapshot.Entry(g.get("group").asString(), c.get("code").asString(),
                            c.get("label").asString(), c.get("parent").isNull() ? null : c.get("parent").asString(),
                            c.get("sortOrder").asInt(), true, attrs));
                }
                groups.add(new CodeSnapshot.Group(g.get("group").asString(), g.get("name").asString(),
                        g.path("description").asString(null), gi * 10, codes));
            }
            return new CodeSnapshot("test#seed", groups);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("코드 초기값을 읽을 수 없어요: " + path.toAbsolutePath(), e);
        }
    }

    /** 일부 코드를 비활성으로 바꾼 사본 */
    public static CodeSnapshot withInactive(String group, String... codes) {
        List<String> off = List.of(codes);
        List<CodeSnapshot.Group> groups = seed().groups().stream()
                .map(g -> new CodeSnapshot.Group(g.group(), g.name(), g.description(), g.sortOrder(), g.codes().stream()
                        .map(e -> e.group().equals(group) && off.contains(e.code())
                                ? new CodeSnapshot.Entry(e.group(), e.code(), e.label(), e.parent(), e.sortOrder(), false,
                                        e.attrs())
                                : e)
                        .toList()))
                .toList();
        return new CodeSnapshot("test#inactive", groups);
    }
}
