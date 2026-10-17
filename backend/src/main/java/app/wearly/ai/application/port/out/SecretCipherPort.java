package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.SealedSecret;
import app.wearly.ai.domain.SecretUnavailableException;

/** 키 암호화 (AES-256-GCM, 마스터 키 WEARLY_SECRET_KEY). AAD로 공급자 ID를 묶는다 */
public interface SecretCipherPort {

  /** 마스터 키가 있어 웹 키를 저장·사용할 수 있는지 */
  boolean enabled();

  SealedSecret seal(String plaintext, String aad);

  /**
   * @throws SecretUnavailableException 마스터 키가 없거나 다르거나, AAD가 다르거나, 암호문이 손상됐을 때
   */
  String open(SealedSecret sealed, String aad);
}
