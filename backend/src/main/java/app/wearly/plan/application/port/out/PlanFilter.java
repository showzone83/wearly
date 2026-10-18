package app.wearly.plan.application.port.out;

import java.time.LocalDate;

/**
 * 예정 목록 조건 (양 끝 포함)
 *
 * @param from null이면 처음부터
 * @param to null이면 끝까지
 */
public record PlanFilter(long userId, LocalDate from, LocalDate to) {}
