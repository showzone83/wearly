package app.wearly.auth.domain;

/** 마스터 키가 없거나 바뀌어 TOTP 비밀을 암호화·복호화할 수 없음. 서비스가 409 SECRET_KEY_MISSING으로 바꾼다 */
public class MfaSecretUnavailableException extends RuntimeException {

  public MfaSecretUnavailableException(String message) {
    super(message);
  }
}
