package app.wearly.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SSO에서 쓰는 무작위 값: state·nonce·PKCE verifier·교환 코드는 모두 32바이트 난수 base64url(43자)이다. 저장소에는 SHA-256 hex만
 * 둔다(RefreshTokens.hash). PKCE challenge는 S256(RFC 7636).
 */
public final class SsoSecrets {

  private SsoSecrets() {}

  public static String newValue() {
    return RefreshTokens.newRawToken();
  }

  public static String hash(String raw) {
    return RefreshTokens.hash(raw);
  }

  /** code_challenge = BASE64URL(SHA-256(ASCII(code_verifier))) */
  public static String s256(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 받은 값이 우리가 만든 모양인지 (base64url 43자). 아니면 저장소를 찾지 않는다 */
  public static boolean isFormat(String raw) {
    return raw != null && raw.matches("^[A-Za-z0-9_-]{43}$");
  }
}
