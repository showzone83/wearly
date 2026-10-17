package app.wearly.auth.application.port.in.dto;

import java.util.List;

/** 세션 목록 한 쪽 (lastSeenAt 최신순)과 살아 있는 세션 전체 수 */
public record SessionSliceDto(List<SessionDto> items, long total) {}
