package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.application.service.AiQuotaService;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 일일 한도가 동시 요청에도 넘치지 않는지 확인한다 (M7, 테스트 한도 3회). */
class AiQuotaConcurrencyIntegrationTest extends IntegrationTestBase {

  static final int THREADS = 12;

  @Autowired AiQuotaService quota;

  @Autowired JdbcTemplate jdbc;

  @Test
  void 동시에_예약해도_한도만큼만_받는다() throws Exception {
    long userId = Long.parseLong(signup().userId());

    List<Optional<AiQuotaService.Reservation>> results =
        runTogether(() -> quota.reserve(userId, "ITEM_ANALYZE", 3));

    List<AiQuotaService.Reservation> granted = results.stream().flatMap(Optional::stream).toList();
    assertThat(granted).hasSize(3);
    assertThat(granted).extracting(AiQuotaService.Reservation::used).containsOnly(1L, 2L, 3L);

    // 다른 기능, 다른 회원은 따로 센다
    assertThat(quota.reserve(userId, "OUTFIT_RECOMMEND", 3)).isPresent();
    assertThat(quota.reserve(Long.parseLong(signup().userId()), "ITEM_ANALYZE", 3)).isPresent();

    // 예약을 지우면 다시 받을 수 있다
    quota.release(granted.get(0));
    assertThat(quota.reserve(userId, "ITEM_ANALYZE", 3)).isPresent();
    assertThat(quota.reserve(userId, "ITEM_ANALYZE", 3)).isEmpty();
  }

  @Test
  void 오래된_예약은_세지_않는다() {
    long userId = Long.parseLong(signup().userId());
    for (int i = 0; i < 3; i++) {
      assertThat(quota.reserve(userId, "ITEM_ANALYZE", 3)).isPresent();
    }
    assertThat(quota.reserve(userId, "ITEM_ANALYZE", 3)).isEmpty();

    // 서버가 죽어 남은 예약(30분보다 오래된 것)은 지우고 다시 받는다
    jdbc.update(
        "update tb_ai_usage_reservation set created_at = created_at - interval '31 minutes'"
            + " where user_id = ?",
        userId);
    assertThat(quota.reserve(userId, "ITEM_ANALYZE", 3)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tb_ai_usage_reservation where user_id = ?",
                Long.class,
                userId))
        .isEqualTo(1L);
  }

  @Test
  void 사진_분석을_동시에_불러도_한도만큼만_성공한다() throws Exception {
    Session s = signup();

    List<Integer> statuses =
        runTogether(
            () ->
                api()
                    .upload(
                        AiAnalyzeIntegrationTest.PATH,
                        s.accessToken(),
                        "image",
                        bytes(),
                        "p.png",
                        "image/png")
                    .status());

    assertThat(statuses).filteredOn(code -> code == 200).hasSize(3);
    assertThat(statuses).filteredOn(code -> code == 429).hasSize(THREADS - 3);
    assertThat(successLogs(s.userId())).isEqualTo(3L);
    // 끝난 호출의 예약은 남지 않는다
    assertThat(reservations(s.userId())).isZero();
  }

  private long successLogs(String userId) {
    return jdbc.queryForObject(
        "select count(*) from tb_ai_analysis_log where user_id = ? and status = 'SUCCESS'",
        Long.class,
        Long.parseLong(userId));
  }

  private long reservations(String userId) {
    return jdbc.queryForObject(
        "select count(*) from tb_ai_usage_reservation where user_id = ?",
        Long.class,
        Long.parseLong(userId));
  }

  /** 같은 작업을 THREADS개 스레드에서 한꺼번에 시작한다 */
  private static <T> List<T> runTogether(Callable<T> task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> f : futures) {
        results.add(f.get());
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private static byte[] bytes() {
    byte[] b = new byte[100];
    new Random().nextBytes(b);
    return b;
  }
}
