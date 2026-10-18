package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.MfaSecretUnavailableException;
import app.wearly.auth.domain.SealedMfaSecret;

/** TOTP 비밀 암호화 (AES-256-GCM, AI 키와 같은 마스터 키 wearly.secret-key). AAD로 회원 ID를 묶는다 */
public interface MfaSecretCipherPort {

  /** 마스터 키가 있어 비밀을 저장할 수 있는지 */
  boolean enabled();

  /**
   * @throws MfaSecretUnavailableException 마스터 키가 없을 때
   */
  SealedMfaSecret seal(String plaintext, String aad);

  /**
   * @throws MfaSecretUnavailableException 마스터 키가 없거나 다르거나, AAD가 다르거나, 암호문이 손상됐을 때
   */
  String open(SealedMfaSecret sealed, String aad);
}
