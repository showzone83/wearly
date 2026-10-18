package app.wearly.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * resources/error-codes.json(앱·관리자가 화면 문구를 정하는 기준)이 ErrorCode·FieldErrorCode enum과 빠짐없이 맞는지 본다: 코드
 * 이름 집합, HTTP 상태, params 이름, 권장 문구(= ErrorCode 설명).
 */
class ErrorCodeCatalogTest {

  static JsonNode catalog() throws IOException {
    try (InputStream in = ErrorCodeCatalogTest.class.getResourceAsStream("/error-codes.json")) {
      assertThat(in).as("error-codes.json이 classpath에 있다").isNotNull();
      return JsonMapper.builder().build().readTree(in);
    }
  }

  @Test
  void 오류_코드가_enum과_빠짐없이_같다() throws IOException {
    Map<String, JsonNode> json = byCode(catalog().get("errorCodes"));
    assertThat(json.keySet())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
    for (ErrorCode c : ErrorCode.values()) {
      JsonNode n = json.get(c.name());
      assertThat(n.get("status").asInt()).as(c + " status").isEqualTo(c.status());
      assertThat(strings(n.get("params"))).as(c + " params").isEqualTo(c.params());
      assertThat(n.get("message").asString()).as(c + " 권장 문구").isEqualTo(c.description());
      assertThat(n.get("description").asString()).as(c + " 설명").isNotBlank();
    }
  }

  @Test
  void 필드_오류_코드가_enum과_빠짐없이_같다() throws IOException {
    Map<String, JsonNode> json = byCode(catalog().get("fieldErrorCodes"));
    assertThat(json.keySet())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(FieldErrorCode.values()).map(Enum::name).toList());
    for (FieldErrorCode c : FieldErrorCode.values()) {
      JsonNode n = json.get(c.name());
      assertThat(strings(n.get("params"))).as(c + " params").isEqualTo(c.params());
      assertThat(n.get("message").asString()).as(c + " 권장 문구").isNotBlank();
      assertThat(n.get("description").asString()).as(c + " 설명").isNotBlank();
      assertThat(n.get("fields").isArray()).as(c + " fields").isTrue();
      // 권장 문구의 {이름} 자리는 params 이름이어야 한다
      java.util.regex.Matcher m =
          java.util.regex.Pattern.compile("\\{(\\w+)}").matcher(n.get("message").asString());
      while (m.find()) {
        assertThat(c.params()).as(c + " 문구 자리 " + m.group(1)).contains(m.group(1));
      }
    }
  }

  private static Map<String, JsonNode> byCode(JsonNode list) {
    Map<String, JsonNode> out = new LinkedHashMap<>();
    list.forEach(
        n -> {
          String code = n.get("code").asString();
          assertThat(out).as("중복 코드 " + code).doesNotContainKey(code);
          out.put(code, n);
        });
    return out;
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(v -> out.add(v.asString()));
    return out;
  }
}
