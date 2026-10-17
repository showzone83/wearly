package app.wearly.admin;

import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.List;

/** 목록 쿼리 page(0부터)·size(기본 20, 최대 100). 범위 밖이면 400 */
record Paging(int page, int size) {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    static Paging of(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        List<FieldError> errors = new ArrayList<>();
        if (p < 0) {
            errors.add(new FieldError("page", "0 이상이어야 해요."));
        }
        if (s < 1 || s > MAX_SIZE) {
            errors.add(new FieldError("size", "1~100이어야 해요."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
        return new Paging(p, s);
    }

    int offset() {
        return (int) Math.min(Integer.MAX_VALUE, (long) page * size);
    }
}
