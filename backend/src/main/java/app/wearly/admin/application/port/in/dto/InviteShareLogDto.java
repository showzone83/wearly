package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/** 초대한 이력 한 줄 (유스케이스 출력). channel = SHARE · SMS · COPY */
public record InviteShareLogDto(
    Long id, Instant createdAt, Long userId, String userEmail, String userName, String channel) {}
