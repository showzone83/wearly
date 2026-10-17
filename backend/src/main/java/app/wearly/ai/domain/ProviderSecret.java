package app.wearly.ai.domain;

import java.time.Instant;

/** 공급자의 웹 입력 키 한 행 (tb_ai_provider_secret). 원문은 없고 암호문과 표시용 끝 4자만 있다 */
public record ProviderSecret(String providerId, SealedSecret sealed, String last4, Long updatedBy, Instant updatedAt) {
}
