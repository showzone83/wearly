package app.wearly.ai.application.port.in;

/** 그 기능의 오늘 한도를 다 썼다. 문구·응답(429)은 부른 쪽이 정한다 */
public class AiLimitExceededException extends RuntimeException {

  public AiLimitExceededException(String feature) {
    super("AI " + feature + " 일일 한도를 다 썼어요.");
  }
}
