package app.wearly.ai.application.port.in;

/** 서버가 요청을 받기 시작할 때 공급자 상태를 로그로 남긴다. 쓸 수 있는 공급자가 없어도 시작은 한다 */
public interface AiStatusUseCase {

  void logStartupStatus();
}
