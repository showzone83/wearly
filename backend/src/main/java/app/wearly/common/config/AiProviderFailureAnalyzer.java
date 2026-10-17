package app.wearly.common.config;

import app.wearly.ai.domain.AiProviderConfigException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** 공급자 설정 오류로 시작이 실패하면 긴 스택 트레이스 대신 원인과 해결 방법을 보여 준다. */
public class AiProviderFailureAnalyzer extends AbstractFailureAnalyzer<AiProviderConfigException> {

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, AiProviderConfigException cause) {
    return new FailureAnalysis(
        "AI 공급자 설정 오류: " + cause.getMessage(),
        "WEARLY_AI_PROVIDER를 비우거나 db로 두면 관리자 화면의 AI 공급자 설정을 쓰고, "
            + "목업으로 띄우려면 ./gradlew bootRunMock (WEARLY_AI_PROVIDER=mock)을 쓰세요.",
        cause);
  }
}
