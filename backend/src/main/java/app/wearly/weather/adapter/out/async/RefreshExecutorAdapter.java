package app.wearly.weather.adapter.out.async;

import app.wearly.weather.application.port.out.RefreshExecutorPort;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** RefreshExecutorPort 구현: 날씨 실행 전용 스레드 하나 (잠금이 있어 한 번에 하나만 돈다) */
@Component
class RefreshExecutorAdapter implements RefreshExecutorPort {

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "weather-refresh");
            t.setDaemon(true);
            return t;
          });

  @Override
  public void submit(Runnable task) {
    executor.execute(task);
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);
  }
}
