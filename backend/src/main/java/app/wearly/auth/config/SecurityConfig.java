package app.wearly.auth.config;

import app.wearly.auth.adapter.in.web.ExpiryValidator;
import app.wearly.auth.adapter.in.web.RestAuthenticationEntryPoint;
import app.wearly.auth.adapter.in.web.UserIdAuthenticationConverter;
import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.config.WearlyProperties;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponseWriter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증 설정: Spring Security 필터 체인(JWT 리소스 서버), JWT 키·인코더·디코더, 비밀번호 해시, 토큰 정책. auth 컨텍스트가 소유한다 (M8, 전에는
 * common.config에 있어 common ↔ auth 순환이 있었다). CORS는 컨텍스트를 모르는 설정이라 common.config.CorsConfig에 있다.
 */
@Configuration
public class SecurityConfig {

  static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
  static final String CODES_PATH = "/api/v1/codes";
  static final String APP_CONFIG_PATH = "/api/v1/app/config";
  static final String WEATHER_PATH = "/api/v1/weather";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint entryPoint,
      ErrorResponseWriter errorWriter,
      AuthenticationUseCase authentication,
      JwtDecoder jwtDecoder)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> {})
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        .requestCache(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, CODES_PATH, APP_CONFIG_PATH, WEATHER_PATH)
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .bearerTokenResolver(bearerTokenResolver())
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(
                        (req, res, e) -> errorWriter.write(res, ErrorCode.FORBIDDEN))
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                    new UserIdAuthenticationConverter(authentication))))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(
                        (req, res, e) -> errorWriter.write(res, ErrorCode.FORBIDDEN)));
    return http.build();
  }

  /**
   * 공개 경로(/api/v1/auth/**, /api/v1/codes, /api/v1/app/config, /api/v1/weather)에서는 만료된 Authorization
   * 헤더가 같이 와도 무시한다.
   */
  static BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      if (path.startsWith(AUTH_PATH_PREFIX)
          || path.equals(CODES_PATH)
          || path.equals(APP_CONFIG_PATH)
          || path.equals(WEATHER_PATH)) {
        return null;
      }
      return delegate.resolve(request);
    };
  }

  @Bean
  SecretKey jwtSecretKey(WearlyProperties properties) {
    byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSecretKey, WearlyProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new ExpiryValidator(clock),
            new JwtIssuerValidator(properties.jwt().issuer()),
            new JwtClaimValidator<String>(
                JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
    return decoder;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  TokenPolicy tokenPolicy(WearlyProperties properties) {
    return new TokenPolicy(properties.jwt().refreshTtl());
  }
}
