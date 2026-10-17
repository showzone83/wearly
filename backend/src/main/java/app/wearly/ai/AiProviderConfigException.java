package app.wearly.ai;

/** AI 공급자 설정이 잘못돼 서버를 시작할 수 없을 때. AiProviderFailureAnalyzer가 안내 문구로 보여 준다. */
public class AiProviderConfigException extends IllegalStateException {

    public AiProviderConfigException(String message) {
        super(message);
    }
}
