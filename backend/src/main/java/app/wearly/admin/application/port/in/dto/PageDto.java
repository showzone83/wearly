package app.wearly.admin.application.port.in.dto;

import java.util.List;
import java.util.function.Function;

/** 관리자 목록 결과 { items, page, size, total } (07 문서 4장). page는 0부터 */
public record PageDto<T>(List<T> items, int page, int size, long total) {

  public <R> PageDto<R> map(Function<T, R> mapper) {
    return new PageDto<>(items.stream().map(mapper).toList(), page, size, total);
  }
}
