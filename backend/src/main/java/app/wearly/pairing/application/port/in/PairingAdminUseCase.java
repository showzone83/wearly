package app.wearly.pairing.application.port.in;

import app.wearly.pairing.application.port.in.dto.PairingAdminEndDto;

/** 관리자의 옷장 도우미 연결 관리 (doc/26 6단계). admin 컨텍스트가 부른다 */
public interface PairingAdminUseCase {

  /**
   * 연결을 강제로 끝낸다 (끝낸 쪽 ADMIN). 연결된 것·승인 대기 모두 가능하다. 양쪽에 알린다. 옷은 주인 옷장에 남는다.
   *
   * <p>오류: 404 NOT_FOUND (없거나 이미 끝남)
   */
  PairingAdminEndDto forceEnd(long pairingId);
}
