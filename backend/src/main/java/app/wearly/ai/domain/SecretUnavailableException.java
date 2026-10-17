package app.wearly.ai.domain;

/** 저장된 키를 복호화할 수 없다 (마스터 키가 바뀌었거나 행이 손상됨). 메시지에 키 값을 넣지 않는다 */
public class SecretUnavailableException extends RuntimeException {

  public SecretUnavailableException(String message) {
    super(message);
  }
}
