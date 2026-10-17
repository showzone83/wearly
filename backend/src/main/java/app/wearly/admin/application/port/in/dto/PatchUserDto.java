package app.wearly.admin.application.port.in.dto;

import app.wearly.common.application.Patch;

/** 회원 부분 수정 { status?, role? } (07 문서 4장). 문자열이 아닌 값은 null(허용되지 않는 값)로 온다 */
public record PatchUserDto(Patch<String> status, Patch<String> role) {}
