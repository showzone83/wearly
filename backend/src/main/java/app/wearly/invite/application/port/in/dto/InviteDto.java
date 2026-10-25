package app.wearly.invite.application.port.in.dto;

/**
 * 내 초대 코드.
 *
 * @param code 정리한 8자 (화면에는 XXXX-XXXX)
 * @param invitedCount 이 코드로 가입한 친구 수 (LINKED)
 */
public record InviteDto(String code, long invitedCount) {}
