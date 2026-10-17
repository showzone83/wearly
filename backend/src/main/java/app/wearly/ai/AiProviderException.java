package app.wearly.ai;

/** 공급자 오류. API는 502 AI_UNAVAILABLE로 응답하고 reason은 로그(error_code)에만 남긴다. */
public class AiProviderException extends RuntimeException {

    private final String reason;

    public AiProviderException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiProviderException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
