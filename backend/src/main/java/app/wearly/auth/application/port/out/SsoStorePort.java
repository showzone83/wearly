package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.SsoGrant;
import app.wearly.auth.domain.SsoState;
import java.time.Duration;
import java.util.Optional;

/**
 * SSO의 짧게 사는 값 (Valkey). 꺼내기는 읽고 지우기를 한 번에 해서(GETDEL) 같은 값을 두 번 쓸 수 없다.
 *
 * <ul>
 *   <li>{@code wearly:sso:state:{stateHash}} 시작 → 콜백 (5분)
 *   <li>{@code wearly:sso:grant:{codeHash}} 콜백 → 교환 (1분)
 * </ul>
 */
public interface SsoStorePort {

  void saveState(String stateHash, SsoState state, Duration ttl);

  Optional<SsoState> takeState(String stateHash);

  void saveGrant(String codeHash, SsoGrant grant, Duration ttl);

  Optional<SsoGrant> takeGrant(String codeHash);
}
