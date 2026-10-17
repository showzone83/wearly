package app.wearly.ai.application.port.out;

import java.util.Optional;

/** api_key_env가 가리키는 환경변수(또는 backend/.env)에서 키를 읽는다. 이름 형식이 틀리거나 비었으면 빈 값 */
public interface ApiKeyEnvPort {

  Optional<String> resolve(String envName);
}
