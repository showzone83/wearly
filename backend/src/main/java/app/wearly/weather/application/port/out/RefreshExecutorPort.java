package app.wearly.weather.application.port.out;

/** 관리자 "지금 실행"을 요청 스레드 밖에서 돌린다 (응답은 runId만 바로 준다) */
public interface RefreshExecutorPort {

  void submit(Runnable task);
}
