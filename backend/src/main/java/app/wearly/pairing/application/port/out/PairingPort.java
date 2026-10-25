package app.wearly.pairing.application.port.out;

import app.wearly.pairing.domain.Pairing;
import java.util.List;
import java.util.Optional;

/** 연결 쓰기·단건 조회 (tb_closet_pairing, JPA) */
public interface PairingPort {

  Optional<Pairing> find(long pairingId);

  /** 두 회원 사이의 끝나지 않은 연결 (PENDING · ACTIVE, 만료된 대기 포함). 같은 방향은 하나뿐이다 (부분 고유 인덱스) */
  Optional<Pairing> findUnended(long ownerId, long helperId);

  /** 이 회원이 주인이나 돕는 사람인 끝나지 않은 연결 (만료된 대기 포함, 탈퇴 신청 때 모두 끝낸다) */
  List<Pairing> findUnendedOf(long userId);

  Pairing save(Pairing pairing);
}
