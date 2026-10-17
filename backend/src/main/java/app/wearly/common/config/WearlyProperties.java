package app.wearly.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    String secretKey,
    // 날씨 정보 (17 문서 5장). 운영값(주기·예보 일수·공급자 사용 여부)은 DB와 관리자 화면에 있다
    @Valid Weather weather) {

  @Override
  public Admin admin() {
    return admin == null ? new Admin(null, null, null) : admin;
  }

  @Override
  public Weather weather() {
    return weather == null ? new Weather(null, null, 0, true, null, null) : weather;
  }

  /** 날씨 공급자 고정값과 키. 키 값은 config/application-local.yml(git 제외)에 둔다 */
  public record Weather(
      // db(기본): 관리자 공급자 설정(tb_weather_provider)대로 부른다. mock: 공급자 대신 목업 날씨
      String provider,
      Duration timeout,
      // 실행·호출 이력 보관 일수 (매일 04:00에 지난 이력을 지운다)
      @Min(0) int logRetentionDays,
      // false면 주기 실행·시작 실행·이력 정리를 하지 않는다 (테스트용). 관리자 "지금 실행"은 그대로 된다
      boolean schedulerEnabled,
      // 공급자 코드(WEATHERAPI) → API 주소
      Map<String, String> baseUrls,
      // 공급자 행의 키 이름(key_name) → 키 값. 없으면 같은 이름의 OS 환경변수를 본다
      Map<String, String> keys) {

    @Override
    public Duration timeout() {
      return timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    @Override
    public int logRetentionDays() {
      return logRetentionDays <= 0 ? 90 : logRetentionDays;
    }

    @Override
    public Map<String, String> baseUrls() {
      return baseUrls == null ? Map.of() : baseUrls;
    }

    @Override
    public Map<String, String> keys() {
      return keys == null ? Map.of() : keys;
    }
  }

  public record Jwt(
      @NotBlank String issuer,
      /* HS256 키. 32바이트(256비트) 이상이어야 한다 */
      @NotBlank @Size(min = 32) String secret,
      @NotNull Duration accessTtl,
      @NotNull Duration refreshTtl) {}

  public record Ai(
      // db(기본) | mock. 검증은 AiRunMode가 한다 (ai.config.AiConfig).
      // 공급자·모델·키 환경변수 이름은 tb_ai_provider에 있다
      String provider,
      @Min(0) int dailyLimit,
      // WEARLY_AI_OUTFIT_DAILY_LIMIT: 회원별 하루 AI 코디 추천 횟수 (사진 분석과 따로, 성공만 센다)
      @Min(0) int outfitDailyLimit,
      /* 공급자 호출 제한 시간 기본값 (공급자 options.timeoutSeconds가 있으면 그 값) */
      @NotNull Duration timeout,
      /* true일 때만 공급자 options.baseUrl을 쓴다 (테스트·프록시용). 기본 false: 키를 다른 주소로 보내지 않게 */
      boolean allowBaseUrl,
      // 공급자 종류(CLAUDE·OPENAI·GEMINI)별 내장 모델 목록. 키가 없거나 제공사 목록을 받지 못했을 때
      // 관리자 화면 모델 드롭다운에 보여 주고, 제공사 목록에서도 같은 ID에 추천 표시를 붙인다 (ai.config.AiConfig)
      Map<String, List<CatalogModel>> modelCatalog) {

    @Override
    public Map<String, List<CatalogModel>> modelCatalog() {
      return modelCatalog == null ? Map.of() : modelCatalog;
    }
  }

  /* 내장 모델 한 줄. displayName이 없으면 id를 쓴다 */
  public record CatalogModel(String id, String displayName, boolean recommended) {}

  /* 시작 시 만들거나 ADMIN으로 올릴 관리자 계정 (07 문서 2장). email·password가 둘 다 있을 때만 */
  public record Admin(String email, String password, String name) {

    public boolean configured() {
      return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
  }

  public record Cors(List<String> allowedOrigins) {}
}
