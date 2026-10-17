package app.wearly.ai.application.port.in;

/** 쓸 수 있는 공급자가 없거나 모두 실패했다. 문구·응답(502)은 부른 쪽이 정한다 */
public class AiUnavailableException extends RuntimeException {

  public AiUnavailableException(String feature) {
    super("AI " + feature + "을(를) 지금 할 수 없어요.");
  }
}
