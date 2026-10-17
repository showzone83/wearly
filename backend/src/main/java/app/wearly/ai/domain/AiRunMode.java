package app.wearly.ai.domain;

import java.util.Locale;

/** WEARLY_AI_PROVIDER 해석 (07 문서 3장) */
public final class AiRunMode {

    public static final String MODE_MOCK = "mock";
    public static final String MODE_DB = "db";

    private AiRunMode() {
    }

    /**
     * mock → 목업(true), db(기본)·claude(1차 기본값, 호환)·빈 값 → DB 공급자(false).
     * 그 밖의 값(예전 auto 포함)은 시작 실패.
     */
    public static boolean isMock(String raw) {
        String mode = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case MODE_MOCK -> true;
            case MODE_DB, "claude", "" -> false;
            case "auto" -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER=auto는 더 이상 지원하지 않습니다. 기본값(db)을 쓰거나 "
                            + "목업은 WEARLY_AI_PROVIDER=mock 또는 ./gradlew bootRunMock으로 띄우세요.");
            default -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER는 db(기본, 관리자 화면의 AI 공급자 설정) 또는 mock이어야 합니다: " + raw);
        };
    }
}
