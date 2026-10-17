package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.PageDto;
import java.util.List;
import java.util.function.Function;

/** 관리자 목록 응답 { items, page, size, total } (07 문서 4장) */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

  public static <D, T> PageResponse<T> from(PageDto<D> page, Function<D, T> item) {
    return new PageResponse<>(
        page.items().stream().map(item).toList(), page.page(), page.size(), page.total());
  }
}
