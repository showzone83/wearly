package app.wearly.common.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 날짜 기준(일일 한도, 최근 착용, 오늘 집계)은 한국 시간이다. */
public final class Zones {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private Zones() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(SEOUL));
    }

    public static Instant startOf(LocalDate day) {
        return day.atStartOfDay(SEOUL).toInstant();
    }
}
