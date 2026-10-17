package app.wearly.ai.adapter.out.crypto;

import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.domain.SealedSecret;
import app.wearly.ai.domain.SecretUnavailableException;
import app.wearly.common.config.WearlyProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM (07 문서 6장). 저장할 때마다 새 무작위 12바이트 nonce, 태그 128비트, AAD = provider_id.
 * 마스터 키는 WEARLY_SECRET_KEY(base64로 인코딩한 32바이트)이고 DB에 두지 않는다.
 * 없으면 웹 키 기능을 끄고 WARN, 형식이 틀리면 끄고 ERROR를 남긴다 (서버는 뜬다). 키 값은 어떤 로그에도 남기지 않는다.
 */
@Component
public class AesGcmSecretCipher implements SecretCipherPort {

    private static final Logger log = LoggerFactory.getLogger(AesGcmSecretCipher.class);

    static final String TRANSFORMATION = "AES/GCM/NoPadding";
    static final int NONCE_BYTES = 12;
    static final int TAG_BITS = 128;
    static final int KEY_BYTES = 32;
    /** 지금 마스터 키 버전 (key_version). 나중에 KMS·키 교체용 */
    public static final int KEY_VERSION = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey masterKey;

    @Autowired
    public AesGcmSecretCipher(WearlyProperties properties) {
        this(properties.secretKey());
    }

    /** @param base64Key WEARLY_SECRET_KEY 값 (없거나 틀리면 꺼진 상태) */
    public AesGcmSecretCipher(String base64Key) {
        this.masterKey = parse(base64Key);
    }

    private static SecretKey parse(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("WEARLY_SECRET_KEY가 없어 관리자 화면의 AI 키 웹 입력을 쓸 수 없어요 (환경변수 키는 그대로 씁니다). "
                    + "켜려면 openssl rand -base64 32 값을 WEARLY_SECRET_KEY에 넣고 다시 시작하세요.");
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            log.error("WEARLY_SECRET_KEY가 base64 형식이 아니에요. AI 키 웹 입력을 끕니다 (openssl rand -base64 32로 다시 만드세요).");
            return null;
        }
        if (bytes.length != KEY_BYTES) {
            log.error("WEARLY_SECRET_KEY는 base64로 인코딩한 32바이트여야 해요 (지금 {}바이트). AI 키 웹 입력을 끕니다.",
                    bytes.length);
            return null;
        }
        log.info("AI 키 웹 입력: 켜짐 (AES-256-GCM, key_version={})", KEY_VERSION);
        return new SecretKeySpec(bytes, "AES");
    }

    @Override
    public boolean enabled() {
        return masterKey != null;
    }

    @Override
    public SealedSecret seal(String plaintext, String aad) {
        if (masterKey == null) {
            throw new IllegalStateException("WEARLY_SECRET_KEY가 없어 암호화할 수 없어요.");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new SealedSecret(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce, KEY_VERSION);
        } catch (GeneralSecurityException e) {
            // 원인 예외에도 평문은 없지만, 메시지는 고정 문구만 쓴다
            throw new IllegalStateException("키를 암호화하지 못했어요: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public String open(SealedSecret sealed, String aad) {
        if (masterKey == null) {
            throw new SecretUnavailableException("WEARLY_SECRET_KEY가 없어요.");
        }
        if (sealed.keyVersion() != KEY_VERSION) {
            throw new SecretUnavailableException("모르는 key_version이에요: " + sealed.keyVersion());
        }
        if (sealed.nonce() == null || sealed.nonce().length != NONCE_BYTES) {
            throw new SecretUnavailableException("nonce가 12바이트가 아니에요.");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, sealed.nonce()));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(sealed.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecretUnavailableException("복호화에 실패했어요 (" + e.getClass().getSimpleName() + ").");
        }
    }
}
