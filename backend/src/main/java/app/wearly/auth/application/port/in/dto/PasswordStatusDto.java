package app.wearly.auth.application.port.in.dto;

import java.time.Instant;

/**
 * 비밀번호 변경 주기 상태 (28 문서)
 *
 * @param changedAt 비밀번호를 마지막으로 정한 시각. 비밀번호가 없는 계정(SSO만)이면 null
 * @param dueAt 바꿔야 하는 시각. 기능이 꺼졌거나 비밀번호가 없으면 null
 * @param due 기한이 지났는지 (앱·관리자 사이트가 변경 알림을 띄운다)
 * @param intervalDays 변경 주기 일수 (연장하면 그만큼 미뤄진다). 기능이 꺼졌으면 0
 */
public record PasswordStatusDto(Instant changedAt, Instant dueAt, boolean due, long intervalDays) {}
