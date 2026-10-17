package app.wearly.auth.domain;

import java.time.Duration;

/** Refresh 토큰 수명 (WEARLY_REFRESH_TTL) */
public record TokenPolicy(Duration refreshTtl) {
}
