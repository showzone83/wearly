package app.wearly.auth.adapter.out.crypto;

import app.wearly.auth.application.port.out.TotpPort;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** TotpPort 구현 (JDK HMAC, 외부 라이브러리 없음) */
@Component
class TotpAdapter implements TotpPort {

  @Override
  public String newSecret() {
    return Totp.newSecret();
  }

  @Override
  public OptionalLong verify(String base32Secret, String code, Instant now, Long lastUsedStep) {
    return Base32.decode(base32Secret)
        .filter(key -> key.length > 0)
        .map(key -> Totp.verify(key, code, now, lastUsedStep))
        .orElse(OptionalLong.empty());
  }

  @Override
  public String otpauthUrl(String issuer, String account, String base32Secret) {
    return Totp.otpauthUrl(issuer, account, base32Secret);
  }
}
