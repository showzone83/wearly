package app.wearly.ai;

import java.util.Locale;

/**
 * WEARLY_AI_PROVIDER로 정하는 실행 방식 (doc/07_관리자_사이트.md 3장).
 * - MOCK: 목업 실행(./gradlew bootRunMock). DB 설정을 무시하고 언제나 목업.
 * - DB(기본): tb_ai_provider에서 쓸 수 있는 공급자를 우선순위 순으로 시도.
 */
public enum AiMode {
    MOCK, DB;

    /** 예전 기본값 claude는 DB 방식으로 받는다 (claude 행이 우선순위 1이라 같은 동작). 그 밖의 값은 시작 실패. */
    public static AiMode parse(String raw) {
        String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "mock" -> MOCK;
            case "", "db", "claude" -> DB;
            case "auto" -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER=auto는 더 이상 지원하지 않습니다. 기본값(db)을 쓰거나 "
                            + "목업은 WEARLY_AI_PROVIDER=mock 또는 ./gradlew bootRunMock으로 띄우세요.");
            default -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER는 db(기본) 또는 mock이어야 합니다: " + raw
                            + " (공급자 선택은 관리자 화면의 AI 공급자에서 합니다)");
        };
    }
}
