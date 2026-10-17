package app.wearly.ai.application.port.out;

import java.util.Optional;

/**
 * api_key_env의 키 이름으로 서버 설정({@code wearly.ai.keys.<이름>} → 같은 이름 OS 환경변수)에서 키를 읽는다. 이름 형식이 틀리거나 비었으면
 * 빈 값
 */
public interface ApiKeyEnvPort {

  Optional<String> resolve(String envName);
}
