package app.wearly.auth.domain;

/** 암호화한 TOTP 비밀 (AES-256-GCM, 마스터 키 wearly.secret-key). ciphertext는 GCM 태그를 포함한다 */
public record SealedMfaSecret(byte[] ciphertext, byte[] nonce, int keyVersion) {}
