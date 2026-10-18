package app.wearly.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.config.ClockConfig;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.wardrobe.application.port.in.ItemCodeUsageUseCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 이슈 #4: 색 1~3개(colors)·예전 color 호환·구입일·V20 기존 행 변환·새 계절 코드 (실제 HTTP + DB) */
class WardrobeColorsPurchaseIntegrationTest extends IntegrationTestBase {

  static final Path V20 = Path.of("src/main/resources/db/migration/V20__item_colors_purchase.sql");
  static final LocalDate TODAY = LocalDate.now(ClockConfig.SEOUL);
  static final String ITEMS = "/api/v1/wardrobe/items";

  @Autowired JdbcTemplate jdbc;

  @Autowired ItemCodeUsageUseCase usage;

  @Test
  void colors와_예전_color_둘_다_받고_응답의_color는_주_색상() {
    Session s = signup();
    Map<String, Object> input = WardrobeIntegrationTest.item("줄무늬 셔츠", "TOP", "SHIRT", "black");
    input.put("colors", List.of("white", "navy"));
    JsonNode both = api().post(ITEMS, s.accessToken(), input).expect(201).body();
    // 둘 다 오면 colors
    assertThat(both.get("color").asString()).isEqualTo("white");
    assertThat(both.get("colors").toString()).isEqualTo("[\"white\",\"navy\"]");

    JsonNode legacy =
        api()
            .post(
                ITEMS,
                s.accessToken(),
                WardrobeIntegrationTest.item("슬랙스", "BOTTOM", "SLACKS", "charcoal"))
            .expect(201)
            .body();
    assertThat(legacy.get("color").asString()).isEqualTo("charcoal");
    assertThat(legacy.get("colors").toString()).isEqualTo("[\"charcoal\"]");

    Map<String, Object> onlyColors = WardrobeIntegrationTest.item("체크 셔츠", "TOP", "SHIRT", null);
    onlyColors.remove("color");
    onlyColors.put("colors", List.of("beige", "brown", "white"));
    JsonNode three = api().post(ITEMS, s.accessToken(), onlyColors).expect(201).body();
    assertThat(three.get("color").asString()).isEqualTo("beige");

    // 단건 조회도 같은 모양
    assertThat(api().get(ITEMS + "/" + three.get("id").asString(), s.accessToken()).body())
        .isEqualTo(three);
  }

  @Test
  void 색_입력_오류() {
    Session s = signup();
    Map<String, Object> none = WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", null);
    none.remove("color");
    assertThat(errors(api().post(ITEMS, s.accessToken(), none)))
        .isEqualTo("[{\"field\":\"colors\",\"code\":\"REQUIRED\"}]");

    Map<String, Object> many = WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", null);
    many.put("colors", List.of("white", "navy", "gray", "black"));
    assertThat(errors(api().post(ITEMS, s.accessToken(), many)))
        .isEqualTo("[{\"field\":\"colors\",\"code\":\"TOO_MANY\",\"params\":{\"max\":3}}]");

    Map<String, Object> dup = WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", null);
    dup.put("colors", List.of("white", "white"));
    assertThat(errors(api().post(ITEMS, s.accessToken(), dup)))
        .isEqualTo("[{\"field\":\"colors[1]\",\"code\":\"DUPLICATE\"}]");

    Map<String, Object> unknown = WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", null);
    unknown.put("colors", List.of("white", "fuchsia"));
    assertThat(errors(api().post(ITEMS, s.accessToken(), unknown)))
        .isEqualTo("[{\"field\":\"colors[1]\",\"code\":\"NOT_ALLOWED\"}]");

    // 예전 color 입력의 오류는 예전처럼 color 필드
    assertThat(
            errors(
                api()
                    .post(
                        ITEMS,
                        s.accessToken(),
                        WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", "fuchsia"))))
        .isEqualTo("[{\"field\":\"color\",\"code\":\"NOT_ALLOWED\"}]");

    // bulk는 items[i]. 를 붙인다
    assertThat(
            errors(
                api().post(ITEMS + "/bulk", s.accessToken(), Map.of("items", List.of(many, dup)))))
        .isEqualTo(
            "[{\"field\":\"items[0].colors\",\"code\":\"TOO_MANY\",\"params\":{\"max\":3}},"
                + "{\"field\":\"items[1].colors[1]\",\"code\":\"DUPLICATE\"}]");
  }

  @Test
  void PATCH_예전_color는_주_색상만_colors는_전부_바꾼다() {
    Session s = signup();
    Map<String, Object> input = WardrobeIntegrationTest.item("셔츠", "TOP", "SHIRT", null);
    input.put("colors", List.of("white", "navy", "gray"));
    String path = ITEMS + "/" + created(s, input).get("id").asString();

    JsonNode primary =
        api().patch(path, s.accessToken(), Map.of("color", "navy")).expect(200).body();
    assertThat(primary.get("colors").toString()).isEqualTo("[\"navy\",\"gray\"]");
    assertThat(primary.get("color").asString()).isEqualTo("navy");

    JsonNode all =
        api()
            .patch(path, s.accessToken(), Map.of("colors", List.of("black"), "color", "white"))
            .expect(200)
            .body();
    assertThat(all.get("colors").toString()).isEqualTo("[\"black\"]");

    assertThat(errors(api().patch(path, s.accessToken(), "{\"color\":null}")))
        .isEqualTo("[{\"field\":\"color\",\"code\":\"REQUIRED\"}]");
    assertThat(errors(api().patch(path, s.accessToken(), "{\"colors\":[]}")))
        .isEqualTo("[{\"field\":\"colors\",\"code\":\"REQUIRED\"}]");
    assertThat(errors(api().patch(path, s.accessToken(), "{\"colors\":\"black\"}")))
        .isEqualTo("[{\"field\":\"colors\",\"code\":\"INVALID_FORMAT\"}]");
  }

  @Test
  void 목록_필터_color는_보조_색도_맞고_통계는_주_색상만_센다() {
    Session s = signup();
    Map<String, Object> striped = WardrobeIntegrationTest.item("줄무늬 셔츠", "TOP", "SHIRT", null);
    striped.put("colors", List.of("white", "navy"));
    created(s, striped);
    created(s, WardrobeIntegrationTest.item("네이비 니트", "TOP", "KNIT", "navy"));
    created(s, WardrobeIntegrationTest.item("흰 티", "TOP", "TEE", "white"));

    assertThat(names(api().get(ITEMS + "?color=navy&sort=name", s.accessToken())))
        .containsExactly("네이비 니트", "줄무늬 셔츠");
    assertThat(names(api().get(ITEMS + "?color=white&sort=name", s.accessToken())))
        .containsExactly("줄무늬 셔츠", "흰 티");

    JsonNode byColor = api().get("/api/v1/wardrobe/stats", s.accessToken()).body().get("byColor");
    assertThat(byColor.toString())
        .isEqualTo("[{\"color\":\"white\",\"count\":2},{\"color\":\"navy\",\"count\":1}]");
  }

  @Test
  void 보조_색으로만_쓰는_코드도_사용_중으로_센다() {
    Session s = signup();
    Map<String, Object> input = WardrobeIntegrationTest.item("라벤더 포인트", "TOP", "SHIRT", null);
    input.put("colors", List.of("white", "lavender"));
    created(s, input);
    Long used =
        jdbc.queryForObject(
            "select count(*) from tb_wardrobe_item where color = 'lavender'"
                + " or 'lavender' = any (sub_colors)",
            Long.class);
    assertThat(used).isPositive();
    // 코드 삭제 가드(countUsing)와 사용 수 목록(countByCode)이 보조 색도 센다
    assertThat(usage.countItemsUsing("COLOR", "lavender")).isEqualTo(used);
    assertThat(usage.countByCode())
        .filteredOn(c -> c.group().equals("COLOR") && c.code().equals("lavender"))
        .singleElement()
        .satisfies(c -> assertThat(c.count()).isEqualTo(used));
  }

  @Test
  void 구입일은_선택이고_비우면_등록일로_넣고_추정_표시() {
    Session s = signup();
    JsonNode unknown = created(s, WardrobeIntegrationTest.item("니트", "TOP", "KNIT", "navy"));
    assertThat(unknown.get("purchasedAt").asString()).isEqualTo(TODAY.toString());
    assertThat(unknown.get("purchasedAtEstimated").asBoolean()).isTrue();
    String path = ITEMS + "/" + unknown.get("id").asString();

    JsonNode known =
        api().patch(path, s.accessToken(), Map.of("purchasedAt", "2024-03-02")).expect(200).body();
    assertThat(known.get("purchasedAt").asString()).isEqualTo("2024-03-02");
    assertThat(known.get("purchasedAtEstimated").asBoolean()).isFalse();
    // 다른 필드만 바꾸면 그대로
    assertThat(
            api()
                .patch(path, s.accessToken(), Map.of("name", "울 니트"))
                .expect(200)
                .body()
                .get("purchasedAt")
                .asString())
        .isEqualTo("2024-03-02");

    JsonNode reset =
        api().patch(path, s.accessToken(), "{\"purchasedAt\":null}").expect(200).body();
    assertThat(reset.get("purchasedAt").asString()).isEqualTo(TODAY.toString());
    assertThat(reset.get("purchasedAtEstimated").asBoolean()).isTrue();

    Map<String, Object> dated = WardrobeIntegrationTest.item("코트", "OUTER", "COAT", "camel");
    dated.put("purchasedAt", "1990-01-01");
    JsonNode old = created(s, dated);
    assertThat(old.get("purchasedAt").asString()).isEqualTo("1990-01-01");
    assertThat(old.get("purchasedAtEstimated").asBoolean()).isFalse();

    Map<String, Object> future = WardrobeIntegrationTest.item("코트", "OUTER", "COAT", "camel");
    future.put("purchasedAt", TODAY.plusDays(1).toString());
    assertThat(errors(api().post(ITEMS, s.accessToken(), future)))
        .isEqualTo(
            "[{\"field\":\"purchasedAt\",\"code\":\"OUT_OF_RANGE\",\"params\":"
                + "{\"min\":\"1990-01-01\",\"max\":\""
                + TODAY
                + "\"}}]");
    Map<String, Object> bad = WardrobeIntegrationTest.item("코트", "OUTER", "COAT", "camel");
    bad.put("purchasedAt", "2024-13-40");
    assertThat(
            api()
                .post(ITEMS, s.accessToken(), bad)
                .expectError(400, "VALIDATION_FAILED")
                .body()
                .get("fieldErrors")
                .get(0)
                .get("field")
                .asString())
        .isEqualTo("purchasedAt");
  }

  @Test
  void V20은_기존_행을_보조_색_없음과_서울_기준_등록일_추정으로_바꾼다() throws Exception {
    long userId = Long.parseLong(signup().userId());
    // 2026-03-01 16:30 UTC = 서울 2026-03-02 01:30
    Long id =
        jdbc.queryForObject(
            "insert into tb_wardrobe_item (user_id, name, category, sub_type, color, pattern,"
                + " formality, purchased_at, created_at)"
                + " values (?, '예전 옷', 'TOP', 'KNIT', 'navy', 'SOLID', 2, date '2000-01-01',"
                + " timestamptz '2026-03-01 16:30:00+00') returning item_id",
            Long.class,
            userId);
    jdbc.execute(backfill() + " WHERE item_id = " + id);

    Map<String, Object> row =
        jdbc.queryForMap(
            "select sub_colors::text as subs, purchased_at::text as bought,"
                + " purchased_at_estimated as est from tb_wardrobe_item where item_id = ?",
            id);
    assertThat(row.get("subs")).isEqualTo("{}");
    assertThat(row.get("bought")).isEqualTo("2026-03-02");
    assertThat(row.get("est")).isEqualTo(Boolean.TRUE);

    // 보조 색은 2개까지, 주 색상과 겹치지 않는다 (DB 제약)
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update tb_wardrobe_item set sub_colors = array['navy'] where item_id = ?", id))
        .hasMessageContaining("ck_wardrobe_item_sub_colors");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update tb_wardrobe_item set purchased_at = date '1989-12-31'"
                        + " where item_id = ?",
                    id))
        .hasMessageContaining("ck_wardrobe_item_purchased_at");
  }

  @Test
  void 새_계절_코드가_정렬_순서대로_있다() {
    JsonNode groups = api().get("/api/v1/codes", null).expect(200).body().get("groups");
    List<String> seasons = new ArrayList<>();
    for (JsonNode g : groups) {
      if (g.get("group").asString().equals("SEASON")) {
        g.get("codes").forEach(c -> seasons.add(c.get("code").asString()));
      }
    }
    assertThat(seasons)
        .containsExactly(
            "EARLY_SPRING", "SPRING", "EARLY_SUMMER", "SUMMER", "EARLY_FALL", "FALL", "WINTER");
    Session s = signup();
    Map<String, Object> input = WardrobeIntegrationTest.item("트렌치", "OUTER", "COAT", "beige");
    input.put("seasons", List.of("EARLY_SPRING", "EARLY_FALL"));
    assertThat(created(s, input).get("seasons").toString())
        .isEqualTo("[\"EARLY_SPRING\",\"EARLY_FALL\"]");
  }

  private JsonNode created(Session s, Map<String, Object> input) {
    return api().post(ITEMS, s.accessToken(), input).expect(201).body();
  }

  private static String errors(Resp r) {
    return r.expectError(400, "VALIDATION_FAILED").body().get("fieldErrors").toString();
  }

  private static List<String> names(Resp r) {
    List<String> out = new ArrayList<>();
    r.expect(200).body().get("items").forEach(n -> out.add(n.get("name").asString()));
    return out;
  }

  /** 마이그레이션 파일의 기존 행 UPDATE 문 그대로 (WHERE는 테스트가 붙인다) */
  private static String backfill() throws Exception {
    String sql = Files.readString(V20, StandardCharsets.UTF_8);
    String update = sql.substring(sql.indexOf("UPDATE tb_wardrobe_item"));
    return update.substring(0, update.indexOf(';'));
  }
}
