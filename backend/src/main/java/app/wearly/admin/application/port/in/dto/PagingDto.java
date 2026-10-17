package app.wearly.admin.application.port.in.dto;

import app.wearly.common.error.ApiException;

/** 목록 페이지 요청. page는 0부터, size 기본 20·최대 100 (07 문서 4장) */
public record PagingDto(int page, int size) {

  public long offset() {
    return (long) page * size;
  }

  /** 쿼리 문자열 값으로 만든다. 범위를 벗어나면 400 VALIDATION_FAILED */
  public static PagingDto of(Integer page, Integer size) {
    int p = page == null ? 0 : page;
    int s = size == null ? 20 : size;
    if (p < 0) {
      throw ApiException.validation("page", "0 이상이어야 해요.");
    }
    if (s < 1 || s > 100) {
      throw ApiException.validation("size", "1~100이어야 해요.");
    }
    return new PagingDto(p, s);
  }
}
