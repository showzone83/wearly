package app.wearly.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * wearly.* 설정. application.yml 기본값을 backend/config/application-local.yml이나 OS 환경변수로 덮어쓴다 (08 문서
 * 6장).
 */
@Validated
@ConfigurationProperties("wearly")
public record WearlyProperties(
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull Ai ai,
    @Valid @NotNull Cors cors,
    Admin admin,
    // WEARLY_SECRET_KEY: 웹 입력 AI 키를 암호화하는 마스터 키 (base64 32바이트).
    // 없으면 웹 키 기능이 꺼진다 (07 문서 6장)
    String secretKey) {

  @Override
  public Admin admin() {
    return admin == null ? new Admin(null, null, null) : admin;
  }

  public record Jwt(
      @NotBlank String issuer,
      /* HS256 키. 32바이트(256비트) 이상이어야 한다 */
      @NotBlank @Size(min = 32) String secret,
      @NotNull Duration accessTtl,
      @NotNull Duration refreshTtl) {}

  public record Ai(
      // db(기본) | mock. 검증은 AiRunMode가 한다 (ApplicationSettingsConfig).
      // 공급자·모델·키 환경변수 이름은 tb_ai_provider에 있다
      String provider,
      @Min(0) int dailyLimit,
      // WEARLY_AI_OUTFIT_DAILY_LIMIT: 회원별 하루 AI 코디 추천 횟수 (사진 분석과 따로, 성공만 센다)
      @Min(0) int outfitDailyLimit,
      /* 공급자 호출 제한 시간 기본값 (공급자 options.timeoutSeconds가 있으면 그 값) */
      @NotNull Duration timeout,
      /* true일 때만 공급자 options.baseUrl을 쓴다 (테스트·프록시용). 기본 false: 키를 다른 주소로 보내지 않게 */
      boolean allowBaseUrl) {}

  /* 시작 시 만들거나 ADMIN으로 올릴 관리자 계정 (07 문서 2장). email·password가 둘 다 있을 때만 */
  public record Admin(String email, String password, String name) {

    public boolean configured() {
      return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
  }

  public record Cors(List<String> allowedOrigins) {}
}
