package app.wearly.common;

import app.wearly.common.error.ApiException;

/** JSON에서 ID는 문자열이다 (계약 4장). */
public final class Ids {

    private Ids() {
    }

    public static String format(Long id) {
        return id == null ? null : Long.toString(id);
    }

    /** 경로의 ID를 숫자로 바꾼다. 숫자가 아니면 없는 리소스로 본다. */
    public static long parse(String id) {
        try {
            long value = Long.parseLong(id);
            if (value <= 0) {
                throw ApiException.notFound();
            }
            return value;
        } catch (NumberFormatException e) {
            throw ApiException.notFound();
        }
    }
}
