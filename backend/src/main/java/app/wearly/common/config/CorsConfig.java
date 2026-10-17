package app.wearly.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** CORS 설정 (wearly.cors.allowed-origins). 보안 필터 체인(auth.config.SecurityConfig)의 cors()가 이 빈을 쓴다 */
@Configuration
public class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(WearlyProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = properties.cors().allowedOrigins();
    config.setAllowedOrigins(origins == null ? List.of() : origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    // X-Wearly-Client: 로그인 세션의 기기 종류 (app | admin | web, 16 문서 4장). 앱·관리자 사이트가 모든 요청에 붙인다
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "If-None-Match", "X-Wearly-Client"));
    config.setExposedHeaders(List.of("WWW-Authenticate", "ETag"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
