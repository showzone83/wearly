package app.wearly.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 시각은 ISO-8601 UTC 문자열로 내보낸다 (계약 4장). */
public final class Times {

    private Times() {
    }

    public static String iso(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
