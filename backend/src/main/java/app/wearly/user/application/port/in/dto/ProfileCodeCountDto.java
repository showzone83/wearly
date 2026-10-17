package app.wearly.user.application.port.in.dto;

/** 코드 하나를 쓰는 프로필 수 */
public record ProfileCodeCountDto(String group, String code, long count) {}
