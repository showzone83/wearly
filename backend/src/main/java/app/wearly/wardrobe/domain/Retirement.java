package app.wearly.wardrobe.domain;

import java.time.Instant;

/**
 * 사용 중지 상태 (13 문서 2장). 아이템에 이 값이 없으면 사용 중이다.
 *
 * @param at 사용 중지한 시각 (이유·메모를 바꿔도 그대로)
 * @param reason RETIRE_REASON 코드
 * @param memo 메모 (없으면 null)
 */
public record Retirement(Instant at, String reason, String memo) {}
