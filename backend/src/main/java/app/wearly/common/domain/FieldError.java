package app.wearly.common.domain;

/** 필드 하나의 검증 오류. 오류 응답의 fieldErrors 항목이 된다 (05 문서 4장). */
public record FieldError(String field, String message) {
}
