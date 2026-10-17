package app.wearly.ai.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 업로드 사진 규칙: 10MB까지, jpeg·png·webp·heic (05 문서 6장) */
public final class AiImages {

  public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

  private AiImages() {}

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
