package app.wearly.invite.application.port.in.dto;

import java.time.Instant;

/** 내 초대 코드로 가입한 친구 (이름·가입 시각만) */
public record InvitedFriendDto(String name, Instant joinedAt) {}
