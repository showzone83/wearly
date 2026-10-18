package app.wearly.auth.config;

import app.wearly.auth.application.port.out.MfaSecretCipherPort;
import app.wearly.auth.domain.MfaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 관리자 2단계 인증 규칙 (22 문서 4.1) */
@Configuration
public class MfaConfig {

  private static final Logger log = LoggerFactory.getLogger(MfaConfig.class);

  @Bean
  MfaPolicy mfaPolicy(AdminMfaProperties properties, MfaSecretCipherPort cipher) {
    boolean required = properties.required() == null || properties.required();
    MfaPolicy policy = new MfaPolicy(required, properties.issuer());
    if (required && !cipher.enabled()) {
      log.error(
          "관리자 2단계 인증이 켜져 있는데(wearly.admin.mfa.required=true) 마스터 키(wearly.secret-key)가 없어 TOTP를 "
              + "등록할 수 없어요. 등록하지 않은 관리자는 관리자 사이트에 들어갈 수 없습니다. "
              + "openssl rand -base64 32 값을 config/application-local.yml의 wearly.secret-key에 넣거나, "
              + "로컬 개발이면 wearly.admin.mfa.required=false로 끄세요.");
    } else {
      log.info("관리자 2단계 인증: {} (issuer={})", required ? "필수" : "등록한 관리자만", policy.issuer());
    }
    return policy;
  }
}
