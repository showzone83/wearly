package app.wearly.user;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import app.wearly.user.application.port.in.WithdrawalPurgeUseCase;
import app.wearly.user.application.port.in.WithdrawalUseCase;
import app.wearly.user.application.port.in.dto.WithdrawalPurgeRunDto;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 탈퇴 예약 삭제 (doc/27 2단계, 실제 DB): 유예 기간이 지난 탈퇴 예정 회원만 지우고(CASCADE) 기록에 삭제를 남긴다. 기한 전·취소한 회원은 남는다. 같은
 * DB를 다른 테스트와 함께 쓰므로 건수가 아니라 이 테스트의 회원만 확인한다
 */
class WithdrawalPurgeIntegrationTest extends IntegrationTestBase {

  static final String PASSWORD = "pass1234";

  @Autowired WithdrawalPurgeUseCase purge;
  @Autowired WithdrawalUseCase withdrawals;
  @Autowired JdbcTemplate jdbc;

  record Member(String email, Session session) {
    long id() {
      return Long.parseLong(session.userId());
    }

    String token() {
      return session.accessToken();
    }
  }

  Member member(String name) {
    String email = uniqueEmail();
    return new Member(email, api().signup(email, PASSWORD, name));
  }

  static Map<String, Object> item(String name) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("category", "TOP");
    m.put("subType", "SHIRT");
    m.put("color", "white");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    return m;
  }

  void withdraw(Member m) {
    api()
        .post("/api/v1/users/me/withdrawal", m.token(), Map.of("reason", "NOT_USEFUL"))
        .expect(200);
  }

  long count(String table, long userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
  }

  Map<String, Object> record(long userId) {
    return jdbc.queryForMap(
        "SELECT * FROM tb_user_withdrawal WHERE user_id = ? ORDER BY withdrawal_id DESC LIMIT 1",
        userId);
  }

  @Test
  void 기한이_지나면_회원과_데이터를_지우고_기록은_남는다() {
    Member m = member("민지");
    api().post("/api/v1/wardrobe/items", m.token(), item("셔츠")).expect(201);
    withdraw(m);

    // 기한 전: 그대로
    purge.purgeDue(Instant.now().plus(Duration.ofDays(29)));
    assertThat(count("tb_user", m.id())).isEqualTo(1);
    assertThat(record(m.id()).get("purged_at")).isNull();

    // 기한 뒤: 회원·자격 증명·옷이 지워지고 기록에 삭제가 남는다
    WithdrawalPurgeRunDto run = purge.purgeDue(Instant.now().plus(Duration.ofDays(31)));
    assertThat(run.purged()).isGreaterThanOrEqualTo(1);
    assertThat(run.failed()).isZero();
    for (String table :
        List.of("tb_user", "tb_user_local_credential", "tb_wardrobe_item", "tb_user_profile")) {
      assertThat(count(table, m.id())).as(table).isZero();
    }
    Map<String, Object> row = record(m.id());
    assertThat(row.get("purged_at")).isNotNull();
    assertThat(row.get("purged_by")).isEqualTo("SCHEDULER");
    assertThat(row.get("reason")).isEqualTo("NOT_USEFUL");

    // 다시 돌려도 같다 (이미 지운 회원은 대상이 아니다)
    purge.purgeDue(Instant.now().plus(Duration.ofDays(31)));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_user_withdrawal WHERE user_id = ?", Long.class, m.id()))
        .isEqualTo(1);

    // 지운 뒤에는 로그인할 수 없고 같은 이메일로 다시 가입할 수 있다
    api()
        .post("/api/v1/auth/login", null, Map.of("email", m.email(), "password", PASSWORD))
        .expectError(401, "INVALID_CREDENTIALS");
    api().signup(m.email(), PASSWORD, "다시");
  }

  @Test
  void 취소한_회원은_지우지_않는다() {
    Member m = member("민지");
    withdraw(m);
    api()
        .post(
            "/api/v1/auth/login",
            null,
            Map.of("email", m.email(), "password", PASSWORD, "cancelWithdrawal", true))
        .expect(200);

    purge.purgeDue(Instant.now().plus(Duration.ofDays(31)));
    assertThat(count("tb_user", m.id())).isEqualTo(1);
    assertThat(record(m.id()).get("purged_at")).isNull();
    assertThat(record(m.id()).get("canceled_by")).isEqualTo("USER");
  }

  @Test
  void 돕는_사람이_지워져도_대신_등록한_옷은_주인에게_남는다() {
    Member owner = member("엄마");
    Member helper = member("딸");
    String code =
        api()
            .post("/api/v1/pairings/codes", owner.token(), Map.of())
            .expect(201)
            .body()
            .get("code")
            .asString();
    String id =
        api()
            .post("/api/v1/pairings/join", helper.token(), Map.of("code", code))
            .expect(201)
            .body()
            .get("id")
            .asString();
    api()
        .post("/api/v1/pairings/" + id + "/approve", owner.token(), Map.of("permission", "EDIT"))
        .expect(200);
    api()
        .post("/api/v1/owners/" + owner.id() + "/wardrobe/items", helper.token(), item("대신 셔츠"))
        .expect(201);

    withdraw(helper);
    purge.purgeDue(Instant.now().plus(Duration.ofDays(31)));

    assertThat(count("tb_user", helper.id())).isZero();
    assertThat(count("tb_wardrobe_item", owner.id())).isEqualTo(1);
  }

  @Test
  void 관리자_바로_삭제는_기한과_상관없이_지우고_탈퇴_예정이_아니면_아무것도_안_한다() {
    Member active = member("사용중");
    assertThat(withdrawals.purgeNow(active.id())).isFalse();
    assertThat(count("tb_user", active.id())).isEqualTo(1);

    Member m = member("민지");
    withdraw(m);
    assertThat(withdrawals.purgeNow(m.id())).isTrue();
    assertThat(count("tb_user", m.id())).isZero();
    assertThat(record(m.id()).get("purged_by")).isEqualTo("ADMIN");
    assertThat(withdrawals.purgeNow(m.id())).isFalse();
  }
}
