package app.wearly.auth.adapter.out.crypto;

import app.wearly.auth.application.port.out.MfaSecretCipherPort;
import app.wearly.auth.domain.MfaSecretUnavailableException;
import app.wearly.auth.domain.SealedMfaSecret;
import app.wearly.common.config.WearlyProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TOTP 비밀 암호화 (AES-256-GCM). AI 키 웹 입력(ai 컨텍스트의 AesGcmSecretCipher)과 같은 마스터 키
 * wearly.secret-key(base64 32바이트)와 같은 방식이다: 저장할 때마다 새 무작위 12바이트 nonce, 태그 128비트, AAD =
 * "mfa:{userId}". 컨텍스트 사이에는 어댑터를 나눠 쓰지 않으므로 짧은 구현을 따로 둔다. 키 값·평문은 어떤 로그·예외 메시지에도 넣지 않는다.
 */
@Component
public class AesGcmMfaSecretCipher implements MfaSecretCipherPort {

  static final String TRANSFORMATION = "AES/GCM/NoPadding";
  static final int NONCE_BYTES = 12;
  static final int TAG_BITS = 128;
  static final int KEY_BYTES = 32;
  static final int KEY_VERSION = 1;

  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey masterKey;

  @Autowired
  public AesGcmMfaSecretCipher(WearlyProperties properties) {
    this(properties.secretKey());
  }

  /**
   * @param base64Key wearly.secret-key 값 (없거나 형식이 틀리면 꺼진 상태. 경고 로그는 AI 쪽 암호화기가 남긴다)
   */
  public AesGcmMfaSecretCipher(String base64Key) {
    this.masterKey = parse(base64Key);
  }

  private static SecretKey parse(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      return null;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(base64Key.strip());
      return bytes.length == KEY_BYTES ? new SecretKeySpec(bytes, "AES") : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public boolean enabled() {
    return masterKey != null;
  }

  @Override
  public SealedMfaSecret seal(String plaintext, String aad) {
    if (masterKey == null) {
      throw new MfaSecretUnavailableException("wearly.secret-key가 없어요.");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      return new SealedMfaSecret(
          cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce, KEY_VERSION);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("비밀을 암호화하지 못했어요: " + e.getClass().getSimpleName());
    }
  }

  @Override
  public String open(SealedMfaSecret sealed, String aad) {
    if (masterKey == null) {
      throw new MfaSecretUnavailableException("wearly.secret-key가 없어요.");
    }
    if (sealed.keyVersion() != KEY_VERSION
        || sealed.nonce() == null
        || sealed.nonce().length != NONCE_BYTES) {
      throw new MfaSecretUnavailableException("모르는 key_version이거나 nonce가 12바이트가 아니에요.");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, sealed.nonce()));
      cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(sealed.ciphertext()), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new MfaSecretUnavailableException("복호화에 실패했어요 (" + e.getClass().getSimpleName() + ").");
    }
  }
}
