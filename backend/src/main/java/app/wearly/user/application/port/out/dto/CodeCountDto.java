package app.wearly.user.application.port.out.dto;

/** 코드별 프로필 수 (그룹, 코드, 개수). 매퍼가 바로 돌려준다 */
public record CodeCountDto(String groupCode, String code, long count) {}
