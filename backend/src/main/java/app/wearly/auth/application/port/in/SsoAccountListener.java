package app.wearly.auth.application.port.in;

/**
 * SSO 로그인이 회원을 만들거나 연결하거나 역할을 바꿨을 때 다른 컨텍스트가 할 일 (UserDeletedListener와 같은 방식: auth가 정의하고 admin의 입력
 * 어댑터가 구현한다). admin은 관리자 변경 이력(tb_admin_audit_log)에 시스템(관리자 없음)이 한 일로 남긴다. 부른 트랜잭션 안에서 부른다.
 */
public interface SsoAccountListener {

  /** IdP 계정으로 새 관리자 회원을 만듦 */
  void accountCreated(long userId, String email, String name, String providerId);

  /** IdP 계정을 같은 이메일의 기존 관리자 회원에 연결함 */
  void accountLinked(long userId, String email, String providerId);

  /** IdP 그룹에 맞춰 역할을 바꿈 (USER → ADMIN 또는 ADMIN → USER) */
  void roleChanged(
      long userId,
      String email,
      String status,
      String beforeRole,
      String afterRole,
      String providerId);
}
