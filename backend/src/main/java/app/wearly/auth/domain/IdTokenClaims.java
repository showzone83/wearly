package app.wearly.auth.domain;

import java.util.List;
import java.util.Locale;

/**
 * 서명·iss·aud·exp·nonce를 확인한 ID 토큰에서 쓰는 값.
 *
 * @param email 소문자로 맞춘 이메일 (없으면 null)
 * @param emailVerified email_verified 클레임 (없으면 null)
 * @param name 표시 이름 후보 (name → preferred_username 순, 없으면 null)
 * @param groups 설정의 roles-claim에서 읽은 그룹·역할 (없으면 빈 목록)
 * @param amr 인증 방법 (RFC 8176, 예: pwd·otp·mfa). 없으면 빈 목록
 */
public record IdTokenClaims(
    String issuer,
    String subject,
    String email,
    Boolean emailVerified,
    String name,
    List<String> groups,
    List<String> amr) {

  /** amr을 기록할 때 쓰는 최대 길이 (tb_auth_event.sso_amr) */
  public static final int AMR_MAX = 100;

  public IdTokenClaims {
    email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    name = name == null || name.isBlank() ? null : name.strip();
    groups = groups == null ? List.of() : List.copyOf(groups);
    amr = amr == null ? List.of() : List.copyOf(amr);
  }

  /** IdP가 여러 요소로 인증했다고 알렸는지 (amr에 mfa·otp·hwk·swk 등). 기록용이다 */
  public boolean idpMfa() {
    return amr.stream()
        .anyMatch(
            a ->
                switch (a) {
                  case "mfa", "otp", "hwk", "swk", "sms", "fpt", "face", "iris", "sc" -> true;
                  default -> false;
                });
  }

  /** 로그인 이력에 남길 amr (쉼표로 이은 영문·숫자만, 100자까지). 없으면 null */
  public String amrText() {
    String s =
        String.join(
            ",",
            amr.stream()
                .map(a -> a.replaceAll("[^A-Za-z0-9_-]", ""))
                .filter(a -> !a.isEmpty())
                .toList());
    if (s.isEmpty()) {
      return null;
    }
    return s.length() > AMR_MAX ? s.substring(0, AMR_MAX) : s;
  }

  /** 새 회원 이름 (1~20자): name → 이메일 앞부분 → "관리자" */
  public String accountName() {
    String n = name;
    if (n == null && email != null) {
      int at = email.indexOf('@');
      n = at > 0 ? email.substring(0, at) : email;
    }
    if (n == null || n.isBlank()) {
      n = "관리자";
    }
    return n.length() > 20 ? n.substring(0, 20) : n;
  }
}
