package app.wearly.admin.application.port.in;

/**
 * 회사 계정(SSO) 로그인이 회원을 만들거나 연결하거나 역할을 바꾼 일을 관리자 변경 이력에 남긴다 (22 문서 4.2). 관리자가 한 일이 아니므로 adminUserId는
 * 비운다(시스템). 부른 트랜잭션 안에서 쓴다.
 *
 * <ul>
 *   <li>SSO_ACCOUNT_CREATE: IdP 계정으로 새 관리자 회원을 만듦
 *   <li>SSO_ACCOUNT_LINK: IdP 계정을 같은 이메일의 기존 관리자 회원에 연결함
 *   <li>USER_ROLE_CHANGE: IdP 그룹에 맞춰 역할을 바꿈 (after.source = "SSO", after.provider)
 * </ul>
 */
public interface AdminSsoAuditUseCase {

  void accountCreated(long userId, String email, String name, String providerId);

  void accountLinked(long userId, String email, String providerId);

  void roleChanged(
      long userId,
      String email,
      String status,
      String beforeRole,
      String afterRole,
      String providerId);
}
