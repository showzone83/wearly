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
import org.springframework.util.unit.DataSize;
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
    @Valid @NotNull Storage storage,
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
    return weather == null
        ? new Weather(null, null, 0, true, null, null, null, null, null, null, null, null)
        : weather;
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
      Map<String, String> keys,
      // 장소별 날씨 (19 문서). REGION 밖 장소 예보 캐시를 그대로 쓰는 시간 (기본 3시간)
      Duration placeCacheTtl,
      // 회원마다 하루(한국 날짜)에 공급자를 실제로 부르게 할 수 있는 좌표 요청 수 (기본 30)
      @Min(0) Integer onDemandDailyLimit,
      // 같은 검색어의 장소 검색 결과를 그대로 쓰는 시간 (기본 1일)
      Duration searchCacheTtl,
      // 회원마다 하루에 공급자를 실제로 부르게 할 수 있는 장소 검색 수 (기본 100)
      @Min(0) Integer searchDailyLimit,
      // 평년값 (Open-Meteo 과거 기상 API)
      @Valid Climate climate,
      // 장소 검색 (Open-Meteo 지오코딩 API)
      @Valid Geocoding geocoding) {

    @Override
    public Duration placeCacheTtl() {
      return placeCacheTtl == null ? Duration.ofHours(3) : placeCacheTtl;
    }

    @Override
    public Integer onDemandDailyLimit() {
      return onDemandDailyLimit == null ? 30 : onDemandDailyLimit;
    }

    @Override
    public Duration searchCacheTtl() {
      return searchCacheTtl == null ? Duration.ofDays(1) : searchCacheTtl;
    }

    @Override
    public Integer searchDailyLimit() {
      return searchDailyLimit == null ? 100 : searchDailyLimit;
    }

    @Override
    public Climate climate() {
      return climate == null ? new Climate(null, null, null, null) : climate;
    }

    @Override
    public Geocoding geocoding() {
      return geocoding == null ? new Geocoding(null, null, null) : geocoding;
    }

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

  /**
   * 평년값 자료 (19 문서 4장). Open-Meteo 무료 API는 비상업용이다: 상업 서비스로 바꿀 때는 유료 플랜 주소(base-url)·키(api-key)를 넣거나
   * enabled=false로 끈다 (끄면 예보 밖 날짜는 평년값 없이 503, 앱은 체감 온도를 직접 고른다).
   */
  public record Climate(
      Boolean enabled,
      // 기본 https://archive-api.open-meteo.com (경로 /v1/archive는 서버가 붙인다)
      String baseUrl,
      // 유료 플랜 키. 비우면 무료 API로 부른다 (config/application-local.yml에 둔다)
      String apiKey,
      // 지난 완결 연도 몇 해로 계산할지 (기본 10)
      @Min(1) Integer years) {

    @Override
    public Boolean enabled() {
      return enabled == null || enabled;
    }

    @Override
    public Integer years() {
      return years == null ? 10 : years;
    }
  }

  /**
   * 장소 검색 (19 문서). Open-Meteo 지오코딩 API로 한글 검색어를 찾고 한글 이름을 준다. 무료 API는 비상업용이다(평년값과 같은 조건): 상업 서비스로
   * 바꿀 때는 유료 플랜 주소(base-url)·키(api-key)를 넣거나 enabled=false로 끈다 (끄거나 실패하면 날씨 공급자 검색으로 대신하고 이름은 영어다).
   */
  public record Geocoding(
      Boolean enabled,
      // 기본 https://geocoding-api.open-meteo.com (경로 /v1/search는 서버가 붙인다)
      String baseUrl,
      // 유료 플랜 키. 비우면 무료 API로 부른다 (config/application-local.yml에 둔다)
      String apiKey) {

    @Override
    public Boolean enabled() {
      return enabled == null || enabled;
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

  /**
   * 옷 사진 저장소 (S3 호환 오브젝트 스토리지, 18 문서). 로컬은 compose의 MinIO, 운영은 AWS S3·Cloudflare R2·NCP Object
   * Storage를 같은 어댑터로 쓴다 (설정만 바꾼다).
   */
  public record Storage(
      // S3 API 주소. 비우면 AWS S3 기본 주소(region으로 정함)
      String endpoint,
      @NotBlank String region,
      @NotBlank String bucket,
      // 비우면 AWS 기본 자격 증명 체인(환경변수·IAM 역할 등)
      String accessKey,
      String secretKey,
      // MinIO·NCP는 true (https://host/bucket/key), AWS·R2는 false도 된다
      boolean pathStyle,
      // 서명 URL에 쓸 주소 (앱이 실제로 접속하는 주소). 비우면 endpoint가 있을 때 요청 Host 이름 + endpoint 포트,
      // endpoint도 없으면(AWS) AWS 기본 주소
      String publicEndpoint,
      // 시작할 때 버킷이 없으면 만든다 (테스트·하네스용. 로컬 compose는 minio-init이 만든다)
      boolean autoCreateBucket,
      @NotNull Duration uploadUrlTtl,
      @NotNull Duration downloadUrlTtl,
      @NotNull DataSize maxUploadBytes,
      // 회원별 하루(Asia/Seoul) 업로드 URL 발급 수
      @Min(0) int dailyUploadLimit,
      // 디코딩 전에 거르는 픽셀 수 상한 (메모리 보호)
      @Min(1) long maxPixels,
      // 동시에 처리(디코딩·다시 인코딩)할 사진 수
      @Min(1) int processingConcurrency,
      // 정리 작업: 이 시간이 지난 PENDING·아이템에 안 붙은 READY·DELETED 이미지를 지운다
      @NotNull Duration staleAfter,
      boolean cleanupEnabled,
      // 정리 작업 간격 (@Scheduled fixedDelay)
      @NotNull Duration cleanupInterval) {}
}
