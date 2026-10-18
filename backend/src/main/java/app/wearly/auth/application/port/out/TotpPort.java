package app.wearly.auth.application.port.out;

import java.time.Instant;
import java.util.OptionalLong;

/** TOTP (RFC 6238: HMAC-SHA1, 6자리, 30초 단계, 앞뒤 1단계 허용). 계산은 출력 어댑터(JDK javax.crypto)가 한다 */
public interface TotpPort {

  /** 새 비밀 (base32 문자열) */
  String newSecret();

  /** 코드가 맞는 단계. lastUsedStep 이하의 단계는 인정하지 않는다 (같은 코드 재사용 막기). 틀리거나 형식이 다르거나 비밀이 base32가 아니면 빈 값 */
  OptionalLong verify(String base32Secret, String code, Instant now, Long lastUsedStep);

  /** 인증 앱 등록 주소 otpauth://totp/… (관리자 사이트가 QR로 그린다) */
  String otpauthUrl(String issuer, String account, String base32Secret);
}
