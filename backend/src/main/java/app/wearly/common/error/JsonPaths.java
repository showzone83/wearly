package app.wearly.common.error;

import java.util.List;
import tools.jackson.databind.DatabindException;

/** Jackson 경로를 `items[3].subType` 같은 필드 이름으로 바꾼다. */
final class JsonPaths {

    private JsonPaths() {
    }

    static String of(List<DatabindException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (DatabindException.Reference ref : path) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? "body" : sb.toString();
    }
}
