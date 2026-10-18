package app.wearly.auth.domain;

import java.time.Duration;

/**
 * 관리자 2단계 인증 규칙 (22 문서 4.1). 대상은 관리자(ADMIN)뿐이다.
 *
 * <ul>
 *   <li>{@code required}(설정 wearly.admin.mfa.required, 기본 true)가 켜져 있거나 이미 TOTP를 등록한 관리자는 관리자 사이트
 *       로그인에서 코드를 확인해야 하고, 코드를 확인한 세션으로만 관리자 API를 쓴다.
 *   <li>꺼져 있고 등록하지 않은 관리자는 예전처럼 비밀번호만으로 들어온다.
 * </ul>
 *
 * @param issuer 인증 앱에 보이는 발급자 이름 (설정 wearly.admin.mfa.issuer, 기본 "Wearly")
 */
public record MfaPolicy(boolean required, String issuer) {

  /** MFA 대기 토큰 수명 */
  public static final Duration PENDING_TTL = Duration.ofMinutes(5);

  /** 등록 시작(setup)에서 만든 비밀을 확인 전까지 들고 있는 시간 */
  public static final Duration SETUP_TTL = Duration.ofMinutes(10);

  /** 이 횟수만큼 틀리면 잠근다 */
  public static final int MAX_FAILURES = 5;

  /** 잠금 시간. 틀린 횟수도 마지막 실패에서 이만큼 지나면 잊는다 */
  public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  public static final String DEFAULT_ISSUER = "Wearly";

  public MfaPolicy {
    issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer.strip();
  }

  /** 그 관리자가 코드를 확인해야 하는지 */
  public boolean needed(boolean enrolled) {
    return required || enrolled;
  }

  /** TOTP 코드 형식: 숫자 6자리 (사이 공백은 무시) */
  public static boolean isTotpFormat(String code) {
    return code != null && code.replaceAll("\\s", "").matches("\\d{6}");
  }
}
