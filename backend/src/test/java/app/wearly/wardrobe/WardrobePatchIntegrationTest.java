package app.wearly.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * M4: PATCH를 타입 있는 부분 명령(PatchItemDto)으로 바꾼 뒤에도 예전 응답과 같다. 형식 오류는 하나만, 검증 오류는 필드 순, 배열 안의 null은 예전
 * 그대로 "seasons[1].&lt;list element&gt;" 필드로 나온다. 없는 아이템이면 본문이 틀려도 404가 먼저다.
 */
class WardrobePatchIntegrationTest extends IntegrationTestBase {

  @Test
  void 형식_오류와_검증_오류() {
    Session s = signup();
    String id = create(s);
    String path = "/api/v1/wardrobe/items/" + id;
    JsonNode errors =
        api()
            .patch(path, s.accessToken(), "{\"seasons\":[\"SPRING\",null],\"name\":\" \"}")
            .expectError(400, "VALIDATION_FAILED")
            .body()
            .get("fieldErrors");
    assertThat(errors.toString())
        .isEqualTo(
            "[{\"field\":\"name\",\"message\":\"이름을 입력해 주세요.\"},"
                + "{\"field\":\"seasons[1].<list element>\",\"message\":\"허용되지 않는 값이에요.\"}]");
    JsonNode typeError =
        api()
            .patch(path, s.accessToken(), "{\"wornCount\":\"x\",\"name\":null,\"formality\":[]}")
            .expectError(400, "VALIDATION_FAILED")
            .body()
            .get("fieldErrors");
    assertThat(typeError.toString())
        .isEqualTo("[{\"field\":\"formality\",\"message\":\"허용되지 않는 값이에요.\"}]");
    api()
        .patch("/api/v1/wardrobe/items/999999999", s.accessToken(), "[]")
        .expectError(404, "NOT_FOUND");
    // 코드 검증은 형식 검증을 통과한 뒤
    api()
        .patch(path, s.accessToken(), Map.of("color", "fuchsia"))
        .expectError(400, "VALIDATION_FAILED");
    JsonNode ok =
        api().patch(path, s.accessToken(), "{\"brand\":null,\"wornCount\":3}").expect(200).body();
    assertThat(ok.get("brand").isNull()).isTrue();
    assertThat(ok.get("wornCount").asInt()).isEqualTo(3);
    assertThat(ok.get("name").asString()).isEqualTo("셔츠");
  }

  private String create(Session s) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", "셔츠");
    m.put("category", "TOP");
    m.put("subType", "SHIRT");
    m.put("color", "white");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    m.put("brand", "브랜드");
    return api()
        .post("/api/v1/wardrobe/items", s.accessToken(), m)
        .expect(201)
        .body()
        .get("id")
        .asString();
  }
}
