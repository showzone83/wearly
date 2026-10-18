package app.wearly.auth.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** TOTP (RFC 6238) 시험 벡터, 앞뒤 1단계 허용, 재사용 막기, base32 */
class TotpTest {

  static final byte[] SEED_SHA1 = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
  static final byte[] SEED_SHA256 =
      "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
  static final byte[] SEED_SHA512 =
      "1234567890123456789012345678901234567890123456789012345678901234"
          .getBytes(StandardCharsets.US_ASCII);

  /** RFC 6238 부록 B (8자리, 30초 단계) */
  @ParameterizedTest
  @CsvSource({
    "59, 94287082, 46119246, 90693936",
    "1111111109, 07081804, 68084774, 25091201",
    "1111111111, 14050471, 67062674, 99943326",
    "1234567890, 89005924, 91819424, 93441116",
    "2000000000, 69279037, 90698825, 38618901",
    "20000000000, 65353130, 77737706, 47863826"
  })
  void RFC_6238_시험_벡터(long epochSeconds, String sha1, String sha256, String sha512) {
    long step = Totp.step(Instant.ofEpochSecond(epochSeconds));
    assertThat(Totp.code(SEED_SHA1, step, 8, "HmacSHA1")).isEqualTo(sha1);
    assertThat(Totp.code(SEED_SHA256, step, 8, "HmacSHA256")).isEqualTo(sha256);
    assertThat(Totp.code(SEED_SHA512, step, 8, "HmacSHA512")).isEqualTo(sha512);
    // 앱이 쓰는 6자리는 같은 값의 끝 6자리다
    assertThat(Totp.code(SEED_SHA1, step)).isEqualTo(sha1.substring(2));
  }

  @Test
  void 앞뒤_1단계까지_받고_그_밖은_거부한다() {
    Instant now = Instant.ofEpochSecond(1_234_567_890);
    long step = Totp.step(now);
    for (long s = step - 1; s <= step + 1; s++) {
      assertThat(Totp.verify(SEED_SHA1, Totp.code(SEED_SHA1, s), now, null)).hasValue(s);
    }
    assertThat(Totp.verify(SEED_SHA1, Totp.code(SEED_SHA1, step - 2), now, null)).isEmpty();
    assertThat(Totp.verify(SEED_SHA1, Totp.code(SEED_SHA1, step + 2), now, null)).isEmpty();
    assertThat(Totp.verify(SEED_SHA1, "12345", now, null)).isEmpty();
    assertThat(Totp.verify(SEED_SHA1, "abcdef", now, null)).isEmpty();
    // 사이 공백은 무시한다
    String code = Totp.code(SEED_SHA1, step);
    assertThat(Totp.verify(SEED_SHA1, code.substring(0, 3) + " " + code.substring(3), now, null))
        .hasValue(step);
  }

  @Test
  void 마지막으로_쓴_단계_이하는_다시_받지_않는다() {
    Instant now = Instant.ofEpochSecond(1_234_567_890);
    long step = Totp.step(now);
    String code = Totp.code(SEED_SHA1, step);
    assertThat(Totp.verify(SEED_SHA1, code, now, step - 1)).hasValue(step);
    assertThat(Totp.verify(SEED_SHA1, code, now, step)).isEmpty();
    // 이전 단계 코드도 이미 지난 단계라 받지 않는다
    assertThat(Totp.verify(SEED_SHA1, Totp.code(SEED_SHA1, step - 1), now, step)).isEmpty();
    // 다음 단계 코드는 받는다
    assertThat(Totp.verify(SEED_SHA1, Totp.code(SEED_SHA1, step + 1), now, step))
        .hasValue(step + 1);
  }

  @Test
  void base32는_RFC_4648과_같고_왕복한다() {
    assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    assertThat(Base32.decode("mzxw6ytboi======"))
        .hasValueSatisfying(
            b -> assertThat(new String(b, StandardCharsets.US_ASCII)).isEqualTo("foobar"));
    assertThat(Base32.decode("MZXW-6YTB OI")).isPresent();
    assertThat(Base32.decode("MZXW1")).isEmpty();
    String secret = Totp.newSecret();
    assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    assertThat(Base32.decode(secret)).hasValueSatisfying(b -> assertThat(b).hasSize(20));
  }

  @Test
  void 인증_앱_등록_주소() {
    assertThat(Totp.otpauthUrl("Wearly", "admin@wearly.app", "JBSWY3DPEHPK3PXP"))
        .isEqualTo(
            "otpauth://totp/Wearly:admin%40wearly.app?secret=JBSWY3DPEHPK3PXP&issuer=Wearly"
                + "&algorithm=SHA1&digits=6&period=30");
  }
}
