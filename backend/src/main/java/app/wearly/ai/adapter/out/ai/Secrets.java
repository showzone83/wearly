package app.wearly.ai.adapter.out.ai;

import java.util.regex.Pattern;

/**
 * 공급자 오류 메시지에서 키를 지운다. 키 값과 키 일부는 어떤 응답·로그에도 내보내지 않는다 (07 문서 3장).
 * 일부 공급자는 인증 오류 메시지에 가린 키 조각을 넣으므로 인증 오류(401·403)는 메시지 자체를 쓰지 않는다.
 */
public final class Secrets {

    static final int MAX_MESSAGE = 300;

    /** 흔한 키 모양 (sk-…, sk-ant-…, AIza…)과 Bearer 토큰 */
    private static final Pattern KEY_LIKE = Pattern.compile(
            "(sk-[A-Za-z0-9_\\-*.]{3,}|AIza[0-9A-Za-z_\\-*.]{6,}|(?i:bearer)\\s+\\S+)");

    private Secrets() {
    }

    public static String scrub(String message, String apiKey) {
        if (message == null) {
            return "";
        }
        String out = message;
        if (apiKey != null && !apiKey.isBlank()) {
            out = out.replace(apiKey, "***");
        }
        out = KEY_LIKE.matcher(out).replaceAll("***");
        return out.length() > MAX_MESSAGE ? out.substring(0, MAX_MESSAGE) + "…" : out;
    }

    /** HTTP 상태별 공급자 오류 메시지. 인증 오류는 원문을 버린다 */
    public static String upstream(String providerLabel, int status, String upstreamMessage, String apiKey) {
        if (status == 401 || status == 403) {
            return providerLabel + " 인증 실패 (HTTP " + status + "). 키 환경변수 값을 확인해 주세요.";
        }
        return providerLabel + " API 오류 (HTTP " + status + "): " + scrub(upstreamMessage, apiKey);
    }
}
