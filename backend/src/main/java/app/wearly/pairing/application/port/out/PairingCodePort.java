package app.wearly.pairing.application.port.out;

import app.wearly.pairing.domain.PairingCode;
import java.time.Duration;
import java.util.Optional;

/** 연결 코드 (Valkey, ttl 뒤 사라진다). 회원은 자리마다 코드를 하나만 가진다 */
public interface PairingCodePort {

  /** 코드를 둔다. 같은 회원·같은 자리의 이전 코드는 지운다. 같은 코드가 이미 있으면 두지 않고 false */
  boolean issue(String code, PairingCode value, Duration ttl);

  /** 코드의 값 (지우지 않는다) */
  Optional<PairingCode> peek(String code);

  /** 코드가 아직 value이면 지우고 true (1회용). 그새 쓰였거나 바뀌었으면 false */
  boolean take(String code, PairingCode value);

  /** 이 회원이 만든 코드를 모두 지운다 (탈퇴 신청, doc/27). 없어도 성공이다 */
  void revokeAll(long issuerId);
}
