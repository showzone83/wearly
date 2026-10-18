package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.SsoLink;
import java.time.Instant;
import java.util.Optional;

/** 회원 ↔ IdP 계정 연결 (tb_user_sso, JPA) */
public interface SsoLinkPort {

  Optional<SsoLink> find(String issuer, String subject);

  /** 새 연결. 같은 (issuer, subject)가 동시에 들어오면 고유 제약 위반이 그대로 올라간다 (409 CONFLICT) */
  SsoLink create(SsoLink link, Instant at);

  /** 로그인 때마다 이메일·공급자 ID·마지막 로그인 시각을 고친다 */
  void touch(long linkId, String email, String providerId, Instant at);
}
