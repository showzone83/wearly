package app.wearly.domain;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** 열거형 ↔ DB 문자열 변환 도우미. 중복을 없애고 순서는 지킨다. */
public final class EnumCodes {

    private EnumCodes() {
    }

    public static <E extends Enum<E>> List<String> names(Collection<E> values) {
        return values.stream().distinct().map(Enum::name).toList();
    }

    public static <E extends Enum<E>> List<E> parse(Collection<String> codes, Function<String, E> parser) {
        return codes == null ? List.of() : codes.stream().map(parser).toList();
    }
}
