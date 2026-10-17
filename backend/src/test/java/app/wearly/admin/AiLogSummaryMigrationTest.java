package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V7가 기존 기록에 채우는 result_summary가 4차 관리자 서비스의 문구와 같은지 (L6) */
class AiLogSummaryMigrationTest extends IntegrationTestBase {

  static final Path V7 = Path.of("src/main/resources/db/migration/V7__ai_log_summary.sql");

  @Autowired JdbcTemplate jdbc;

  @Test
  void 예전_기록의_요약을_예전_문구대로_채운다() throws Exception {
    long userId = Long.parseLong(signup().userId());
    final long analyze =
        insert(
            userId,
            "{\"name\":\"네이비 니트\",\"category\":\"TOP\",\"subType\":\"KNIT\",\"color\":\"navy\"}");
    final long outfit = insert(userId, "{\"candidates\":[{},{},{}],\"dropped\":[]}");
    final long missing = insert(userId, "{\"category\":\"TOP\"}");
    final long failed = insert(userId, null);

    jdbc.execute(
        backfill()
            .replace(
                "WHERE result IS NOT NULL",
                "WHERE user_id = " + userId + " AND result IS NOT NULL"));

    assertThat(summary(analyze)).isEqualTo("네이비 니트 · TOP/KNIT · navy");
    assertThat(summary(outfit)).isEqualTo("코디 후보 3개");
    // 4차 코드: 이름이 없으면 빈 문자열, 다른 값이 없으면 "null"
    assertThat(summary(missing)).isEqualTo(" · TOP/null · null");
    assertThat(summary(failed)).isNull();
  }

  private long insert(long userId, String resultJson) {
    return jdbc.queryForObject(
        "insert into tb_ai_analysis_log (user_id, feature, provider, status, latency_ms, result)"
            + " values (?, 'ITEM_ANALYZE', 'mock', ?, 1, cast(? as jsonb))"
            + " returning ai_analysis_log_id",
        Long.class,
        userId,
        resultJson == null ? "FAILED" : "SUCCESS",
        resultJson);
  }

  private String summary(long id) {
    return jdbc.queryForObject(
        "select result_summary from tb_ai_analysis_log where ai_analysis_log_id = ?",
        String.class,
        id);
  }

  /** 마이그레이션 파일의 UPDATE 문 그대로 */
  private static String backfill() throws Exception {
    String sql = Files.readString(V7, StandardCharsets.UTF_8);
    String update = sql.substring(sql.indexOf("UPDATE tb_ai_analysis_log"));
    return update.substring(0, update.indexOf(';'));
  }
}
