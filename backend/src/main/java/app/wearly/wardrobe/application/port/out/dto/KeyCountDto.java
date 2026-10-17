package app.wearly.wardrobe.application.port.out.dto;

/** 집계 결과 한 줄 (키, 개수). 매퍼가 바로 돌려준다 */
public record KeyCountDto(String key, long count) {}
