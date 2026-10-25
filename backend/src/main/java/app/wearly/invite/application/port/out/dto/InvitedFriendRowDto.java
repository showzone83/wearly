package app.wearly.invite.application.port.out.dto;

import java.time.Instant;

/** 초대로 가입한 친구 한 행 (joinedAt = 가입 이력 시각) */
public record InvitedFriendRowDto(String name, Instant joinedAt) {}
