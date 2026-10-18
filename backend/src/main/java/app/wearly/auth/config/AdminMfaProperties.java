package app.wearly.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wearly.admin.mfa.* (22 문서 4.1). 관리자 2단계 인증(TOTP).
 *
 * @param required 기본 true. 켜져 있으면 TOTP를 등록하지 않은 관리자도 관리자 사이트 로그인 뒤 등록을 마쳐야 관리자 API를 쓴다. false면 등록한
 *     관리자만 코드를 확인한다 (로컬 개발·하네스)
 * @param issuer 인증 앱에 보이는 이름 (기본 "Wearly")
 */
@ConfigurationProperties("wearly.admin.mfa")
public record AdminMfaProperties(Boolean required, String issuer) {}
