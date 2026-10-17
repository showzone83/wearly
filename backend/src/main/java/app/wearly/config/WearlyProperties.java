package app.wearly.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** wearly.* 설정. application.yml에서 환경변수로 덮어쓴다. */
@Validated
@ConfigurationProperties("wearly")
public record WearlyProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Ai ai, @Valid @NotNull Cors cors,
        Admin admin) {

    public record Jwt(
            @NotBlank String issuer,
            /* HS256 키. 32바이트(256비트) 이상이어야 한다 */
            @NotBlank @Size(min = 32) String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {
    }

    public record Ai(
            /* db(기본) | mock. 검증은 AiMode가 한다. 공급자·모델·키 환경변수는 tb_ai_provider에서 관리한다 */
            String provider,
            @Min(0) int dailyLimit,
            /* 공급자 옵션 timeoutSeconds가 없을 때의 호출 제한 시간 */
            @NotNull Duration timeout) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** 시작 시 만들거나 ADMIN으로 올릴 관리자 계정 (WEARLY_ADMIN_*). email·password가 모두 있을 때만 */
    public record Admin(String email, String password, String name) {

        public boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
