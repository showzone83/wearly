package app.wearly.ai.domain;

/** 암호화한 키 (AES-256-GCM). ciphertext는 GCM 태그를 포함한다 */
public record SealedSecret(byte[] ciphertext, byte[] nonce, int keyVersion) {
}
