package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.adapter.out.persistence.mybatis.dto.AiLogRowDto;
import app.wearly.ai.adapter.out.persistence.mybatis.mapper.AiUsageMapper;
import app.wearly.ai.application.service.AiAnalysisService;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 목업 공급자로 /wardrobe/items/analyze를 확인한다 (테스트 한도 3회). */
class AiAnalyzeIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/wardrobe/items/analyze";

  @Autowired AiUsageMapper usageMapper;

  @Autowired JdbcTemplate jdbc;

  @Autowired CodeCatalog catalog;

  @Test
  void 같은_사진은_같은_결과이고_짝이_맞는_제안을_준다() {
    Session s = signup();
    byte[] photo = bytes(5000, 1);
    Resp a = api().upload(PATH, s.accessToken(), "image", photo, "a.jpg", "image/jpeg").expect(200);
    Resp b = api().upload(PATH, s.accessToken(), "image", photo, "b.jpg", "image/jpeg").expect(200);

    assertThat(a.body().get("suggestion")).isEqualTo(b.body().get("suggestion"));
    assertThat(a.body().get("provider").asString()).isEqualTo("mock");
    assertThat(a.body().get("model").isNull()).isTrue();
    assertThat(a.body().get("remainingToday").asInt()).isEqualTo(2);
    assertThat(b.body().get("remainingToday").asInt()).isEqualTo(1);

    var sug = a.body().get("suggestion");
    String subType = sug.get("subType").asString();
    assertThat(catalog.current().belongsTo(subType, sug.get("category").asString())).isTrue();
    assertThat(sug.get("name").asString()).isNotBlank();
    assertThat(sug.get("formality").asInt()).isBetween(1, 3);
    assertThat(sug.get("confidence").asDouble()).isBetween(0.0, 1.0);
    assertThat(sug.get("seasons").size()).isPositive();
    assertThat(sug.get("styles").size()).isPositive();

    // 기록: MyBatis로 JSONB 결과를 Map으로 읽는다
    List<AiLogRowDto> logs = usageMapper.findRecent(Long.parseLong(s.userId()), 10);
    assertThat(logs)
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.getStatus()).isEqualTo("SUCCESS");
              assertThat(row.getProvider()).isEqualTo("mock");
              assertThat(row.getImageSha256()).isEqualTo(AiAnalysisService.sha256(photo));
              assertThat(row.getFeature()).isEqualTo("ITEM_ANALYZE");
              assertThat(row.getResult()).containsEntry("subType", subType);
            });
  }

  @Test
  void 일일_한도를_넘으면_429_AI_LIMIT_EXCEEDED() {
    Session s = signup();
    for (int i = 0; i < 3; i++) {
      Resp r =
          api()
              .upload(PATH, s.accessToken(), "image", bytes(100, i), "p.png", "image/png")
              .expect(200);
      assertThat(r.body().get("remainingToday").asInt()).isEqualTo(2 - i);
    }
    api()
        .upload(PATH, s.accessToken(), "image", bytes(100, 9), "p.png", "image/png")
        .expectError(429, "AI_LIMIT_EXCEEDED");

    // 다른 회원은 영향 없음
    api()
        .upload(PATH, signup().accessToken(), "image", bytes(100, 9), "p.png", "image/png")
        .expect(200);

    // 어제(한국 시간) 기록은 세지 않는다
    jdbc.update(
        "update tb_ai_analysis_log set created_at = created_at - interval '1 day'"
            + " where user_id = ?",
        Long.parseLong(s.userId()));
    api().upload(PATH, s.accessToken(), "image", bytes(100, 9), "p.png", "image/png").expect(200);
  }

  @Test
  void 이미지가_10MB를_넘으면_413_IMAGE_TOO_LARGE() {
    Session s = signup();
    byte[] big = new byte[10 * 1024 * 1024 + 1];
    api()
        .upload(PATH, s.accessToken(), "image", big, "big.jpg", "image/jpeg")
        .expectError(413, "IMAGE_TOO_LARGE");

    byte[] exact = new byte[10 * 1024 * 1024];
    exact[0] = 1;
    api().upload(PATH, s.accessToken(), "image", exact, "max.jpg", "image/jpeg").expect(200);
  }

  @Test
  void 형식과_파트_검증() {
    Session s = signup();
    api()
        .upload(PATH, s.accessToken(), "image", "hello".getBytes(), "a.txt", "text/plain")
        .expectError(400, "VALIDATION_FAILED");
    api()
        .upload(PATH, s.accessToken(), "photo", bytes(10, 1), "a.jpg", "image/jpeg")
        .expectError(400, "VALIDATION_FAILED");
    api()
        .upload(PATH, s.accessToken(), "image", new byte[0], "a.jpg", "image/jpeg")
        .expectError(400, "VALIDATION_FAILED");

    // 형식을 안 밝혀도 첫 바이트로 알아낸다 (PNG, HEIC)
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    api().upload(PATH, s.accessToken(), "image", png, "a", "application/octet-stream").expect(200);
    byte[] heic = new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0};
    api().upload(PATH, s.accessToken(), "image", heic, "a", null).expect(200);

    api().upload(PATH, null, "image", png, "a.png", "image/png").expectError(401, "UNAUTHORIZED");
    assertThat(usageMapper.findRecent(Long.parseLong(s.userId()), 10)).hasSize(2);
  }

  private static byte[] bytes(int size, long seed) {
    byte[] b = new byte[size];
    new Random(seed).nextBytes(b);
    return b;
  }
}
