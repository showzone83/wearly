package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 초대한 이력 한 행 (회원 이메일·이름 포함) */
public record InviteShareRowDto(
    Long id, Instant createdAt, Long userId, String userEmail, String userName, String channel) {}
