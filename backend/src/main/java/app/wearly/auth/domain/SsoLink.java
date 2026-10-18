package app.wearly.auth.domain;

/**
 * 회원과 IdP 계정의 연결 (tb_user_sso, 22 문서 4.2). IdP 계정은 발급자(iss)와 subject(sub)로 정한다 — 이메일은 바뀔 수 있어 처음
 * 연결에만 쓴다.
 *
 * @param providerId 연결할 때의 설정 공급자 ID (표시·기록용)
 * @param email 마지막 로그인 때의 이메일 (참고용, 없으면 null)
 */
public record SsoLink(
    Long id, long userId, String providerId, String issuer, String subject, String email) {}
