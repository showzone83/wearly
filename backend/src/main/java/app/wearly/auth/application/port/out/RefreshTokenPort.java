package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Refresh 토큰 저장·로드 (JPA) */
public interface RefreshTokenPort {

    /** 동시에 같은 토큰으로 갱신하면 한 요청만 회전하도록 행을 잠근다 */
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);

    /** 새 토큰이면 넣고(id 채움), 있는 토큰이면 폐기·회전 상태를 바꾼다 */
    RefreshToken save(RefreshToken token);

    void revokeFamily(UUID familyId, Instant at);

    void revokeAllForUser(long userId, Instant at);
}
