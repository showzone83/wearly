package app.wearly.auth.domain;

import java.time.Instant;

/**
 * 관리자 TOTP 등록 (tb_user_mfa).
 *
 * @param lastUsedStep 마지막으로 받은 30초 단계 (같은 코드 재사용 막기, 없으면 null)
 */
public record MfaEnrollment(
    long userId, SealedMfaSecret secret, Long lastUsedStep, Instant enrolledAt) {

  /** 암호화한 비밀의 AAD. 다른 회원 행에 옮겨 붙이면 복호화되지 않는다 */
  public static String aad(long userId) {
    return "mfa:" + userId;
  }
}
