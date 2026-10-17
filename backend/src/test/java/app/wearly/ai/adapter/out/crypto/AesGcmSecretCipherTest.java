package app.wearly.ai.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.domain.SealedSecret;
import app.wearly.ai.domain.SecretUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** AES-256-GCM 키 암호화 (07 문서 6장, Spring 없이) */
@ExtendWith(OutputCaptureExtension.class)
class AesGcmSecretCipherTest {

    static final String KEY = "sk-test-cipher-0123456789abcdef";

    static String masterKey() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void 암호화하고_같은_AAD로_복호화하면_원문이_나온다() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        assertThat(cipher.enabled()).isTrue();
        SealedSecret sealed = cipher.seal(KEY, "openai");
        assertThat(sealed.nonce()).hasSize(12);
        assertThat(sealed.keyVersion()).isEqualTo(1);
        // 암호문 = 평문 길이 + GCM 태그 16바이트, 평문이 그대로 들어 있지 않다
        assertThat(sealed.ciphertext()).hasSize(KEY.length() + 16);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain(KEY);
        assertThat(cipher.open(sealed, "openai")).isEqualTo(KEY);
    }

    @Test
    void nonce는_매번_다르고_같은_키도_암호문이_다르다() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        SealedSecret a = cipher.seal(KEY, "openai");
        SealedSecret b = cipher.seal(KEY, "openai");
        assertThat(a.nonce()).isNotEqualTo(b.nonce());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    void AAD가_다르면_다른_공급자_행에_붙여_넣어도_복호화되지_않는다() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        SealedSecret sealed = cipher.seal(KEY, "openai");
        assertThatThrownBy(() -> cipher.open(sealed, "gemini")).isInstanceOf(SecretUnavailableException.class)
                .message().doesNotContain(KEY);
    }

    @Test
    void 마스터_키가_다르거나_암호문이_바뀌면_복호화되지_않는다() {
        SealedSecret sealed = new AesGcmSecretCipher(masterKey()).seal(KEY, "openai");
        assertThatThrownBy(() -> new AesGcmSecretCipher(masterKey()).open(sealed, "openai"))
                .isInstanceOf(SecretUnavailableException.class);

        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        SealedSecret ok = cipher.seal(KEY, "openai");
        byte[] tampered = ok.ciphertext().clone();
        tampered[0] ^= 1;
        assertThatThrownBy(() -> cipher.open(new SealedSecret(tampered, ok.nonce(), 1), "openai"))
                .isInstanceOf(SecretUnavailableException.class);
        assertThatThrownBy(() -> cipher.open(new SealedSecret(ok.ciphertext(), ok.nonce(), 2), "openai"))
                .isInstanceOf(SecretUnavailableException.class);
    }

    @Test
    void 마스터_키가_없으면_WARN_형식이_틀리면_ERROR이고_꺼진다(CapturedOutput output) {
        AesGcmSecretCipher missing = new AesGcmSecretCipher("");
        assertThat(missing.enabled()).isFalse();
        assertThat(output.getAll()).contains("WARN").contains("WEARLY_SECRET_KEY가 없어");

        AesGcmSecretCipher notBase64 = new AesGcmSecretCipher("not base64 !!");
        assertThat(notBase64.enabled()).isFalse();
        AesGcmSecretCipher shortKey = new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[16]));
        assertThat(shortKey.enabled()).isFalse();
        assertThat(output.getAll()).contains("ERROR").contains("base64 형식이 아니에요").contains("지금 16바이트");
        assertThatThrownBy(() -> missing.open(new SealedSecret(new byte[1], new byte[12], 1), "x"))
                .isInstanceOf(SecretUnavailableException.class);
    }
}
