package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.LocalCredential;
import java.time.Instant;
import java.util.Optional;

/** 이메일 자격 증명 저장·로드 (JPA) */
public interface CredentialPort {

    Optional<LocalCredential> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<LocalCredential> findByUserId(long userId);

    /** 새 자격 증명. 같은 이메일이 동시에 들어오면 409 EMAIL_TAKEN */
    void create(LocalCredential credential);

    void markLogin(long credentialId, Instant at);
}
