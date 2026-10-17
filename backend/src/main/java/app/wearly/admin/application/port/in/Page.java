package app.wearly.admin.application.port.in;

import app.wearly.common.error.ApiException;
import java.util.List;

/** 관리자 목록 응답 { items, page, size, total } (07 문서 4장). page는 0부터, size 기본 20·최대 100 */
public record Page<T>(List<T> items, int page, int size, long total) {

    public record Paging(int page, int size) {

        public long offset() {
            return (long) page * size;
        }
    }

    public static Paging paging(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        if (p < 0) {
            throw ApiException.validation("page", "0 이상이어야 해요.");
        }
        if (s < 1 || s > 100) {
            throw ApiException.validation("size", "1~100이어야 해요.");
        }
        return new Paging(p, s);
    }
}
