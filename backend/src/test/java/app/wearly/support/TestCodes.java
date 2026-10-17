package app.wearly.support;

import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeGroupEntry;
import app.wearly.common.domain.code.CodeSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 단위 테스트용 코드 스냅샷: 계약 원본 doc/06_코드_초기값.json을 그대로 읽는다 (DB 없이) */
public final class TestCodes {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final Path SEED = Path.of("..", "doc", "06_코드_초기값.json");

  private static CodeSnapshot seed;

  private TestCodes() {}

  public static synchronized CodeSnapshot seed() {
    if (seed == null) {
      seed = load(c -> true);
    }
    return seed;
  }

  /** 일부 코드를 비활성으로 바꾼 스냅샷 ("GROUP:CODE") */
  public static CodeSnapshot withInactive(String... groupCodes) {
    List<String> off = List.of(groupCodes);
    return load(key -> !off.contains(key));
  }

  private interface ActiveRule {
    boolean active(String groupColonCode);
  }

  private static CodeSnapshot load(ActiveRule rule) {
    try {
      JsonNode root = JSON.readTree(Files.readString(SEED));
      List<CodeGroupEntry> groups = new ArrayList<>();
      int order = 10;
      for (JsonNode g : root.get("groups")) {
        List<CodeEntry> codes = new ArrayList<>();
        for (JsonNode c : g.get("codes")) {
          Map<String, Object> attrs = JSON.convertValue(c.get("attrs"), new TypeReference<>() {});
          String group = g.get("group").asString();
          String code = c.get("code").asString();
          codes.add(
              new CodeEntry(
                  code,
                  c.get("label").asString(),
                  c.get("parent").isNull() ? null : c.get("parent").asString(),
                  c.get("sortOrder").asInt(),
                  rule.active(group + ":" + code),
                  attrs));
        }
        groups.add(
            new CodeGroupEntry(
                g.get("group").asString(),
                g.get("name").asString(),
                g.get("description").asString(),
                order,
                codes));
        order += 10;
      }
      return new CodeSnapshot("test", groups);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
