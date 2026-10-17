package app.wearly.wardrobe.application.port.in.dto;

/** 코드 하나를 쓰는 아이템 수 */
public record ItemCodeCountDto(String group, String code, long count) {}
