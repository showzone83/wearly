package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.ProviderSecret;
import java.util.Optional;

/** 공급자 웹 입력 키 저장·로드 (JPA, tb_ai_provider_secret). 공급자 행을 지우면 FK로 함께 지워진다 */
public interface ProviderSecretPort {

    Optional<ProviderSecret> find(String providerId);

    /** 있으면 바꾸고 없으면 넣는다 (updated_at은 지금) */
    ProviderSecret save(ProviderSecret secret);

    /** @return 지운 행이 있었는지 */
    boolean delete(String providerId);
}
