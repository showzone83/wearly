package app.wearly.outfit;

import static app.wearly.outfit.OutfitTestData.PATH;
import static app.wearly.outfit.OutfitTestData.context;
import static app.wearly.outfit.OutfitTestData.item;
import static app.wearly.outfit.OutfitTestData.request;
import static app.wearly.outfit.OutfitTestData.wardrobe;
import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** AI 코디 추천에서 사용 중지한 옷 빼기 (13 문서 3장) */
class OutfitRetiredItemIntegrationTest extends IntegrationTestBase {

  @Test
  void 사용_중지한_옷은_후보에_없고_고정_중심으로_주면_400() {
    Session s = signup();
    String t = s.accessToken();
    Map<String, String> ids = wardrobe(api(), t);
    String knit = ids.get("네이비 니트");
    String sneakers = ids.get("화이트 스니커즈");
    retire(t, knit);
    retire(t, sneakers);

    JsonNode candidates =
        api().post(PATH, t, request(context())).expect(200).body().get("candidates");
    assertThat(candidates).isNotEmpty();
    for (JsonNode c : candidates) {
      assertThat(c.get("items").toString()).doesNotContain(knit).doesNotContain(sneakers);
    }

    Map<String, Object> ctx = context();
    ctx.put("fixed", Map.of("SHOES", sneakers));
    ctx.put("anchorItemId", knit);
    Resp r = api().post(PATH, t, request(ctx)).expectError(400, "VALIDATION_FAILED");
    assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactlyInAnyOrder("context.fixed.SHOES", "context.anchorItemId");
    assertThat(r.body().get("fieldErrors").findValuesAsString("code")).containsOnly("ITEM_RETIRED");
  }

  @Test
  void 사용_중지한_옷만_있는_칸은_옷이_모자란_것으로_본다() {
    Session s = signup();
    String t = s.accessToken();
    Map<String, String> ids =
        OutfitTestData.create(
            api(),
            t,
            List.of(
                item("화이트 셔츠", "TOP", "SHIRT", "white"),
                item("차콜 슬랙스", "BOTTOM", "SLACKS", "charcoal"),
                item("블랙 로퍼", "SHOES", "LOAFER", "black")));
    retire(t, ids.get("블랙 로퍼"));
    Resp r = api().post(PATH, t, request(context())).expectError(400, "WARDROBE_INSUFFICIENT");
    assertThat(r.body().get("params").get("missing").toString()).isEqualTo("[\"SHOES\"]");
  }

  private void retire(String token, String id) {
    api()
        .post("/api/v1/wardrobe/items/" + id + "/retire", token, Map.of("reason", "NOT_WORN"))
        .expect(200);
  }
}
