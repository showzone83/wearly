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
public record WearlyProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Ai ai, @Valid @NotNull Cors cors) {

    public record Jwt(
            @NotBlank String issuer,
            /* HS256 키. 32바이트(256비트) 이상이어야 한다 */
            @NotBlank @Size(min = 32) String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {
    }

    public record Ai(
            /* claude(기본) | mock. 검증은 AiProviderConfig가 한다 */
            @NotBlank String provider,
            @NotBlank String model,
            @Min(0) int dailyLimit,
            String apiKey,
            /* Claude effort: low | medium | high | xhigh | max */
            @NotBlank String effort,
            @NotNull Duration timeout) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
