package app.wearly.ai;

import java.util.regex.Pattern;

/** 공급자 오류 메시지를 로그·관리자 응답에 남기기 전에 키·키 일부로 보이는 문자열을 지운다. */
final class Redactor {

    /** OpenAI(sk-…), Anthropic(sk-ant-…), Google(AIza…) 키 모양. 가린 키(sk-ab***yz)도 지운다 */
    private static final Pattern KEY_LIKE = Pattern.compile("(sk-[A-Za-z0-9_*.\\-]{2,}|AIza[0-9A-Za-z_*\\-]{4,})");

    static final int MAX = 300;

    private Redactor() {
    }

    static String redact(String message, String apiKey) {
        if (message == null) {
            return null;
        }
        String out = message;
        if (apiKey != null && apiKey.length() >= 4) {
            out = out.replace(apiKey, "[redacted]");
        }
        out = KEY_LIKE.matcher(out).replaceAll("[redacted]");
        out = out.replaceAll("\\s+", " ").strip();
        return out.length() > MAX ? out.substring(0, MAX) + "…" : out;
    }
}
