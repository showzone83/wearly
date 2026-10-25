package app.wearly.pairing.application.port.in;

import app.wearly.pairing.application.port.in.dto.ClosetAccessDto;
import app.wearly.pairing.application.port.in.dto.ProxyAction;

/**
 * 돕는 사람이 옷장 주인의 옷장을 쓸 수 있는지 확인하고 대리 작업을 기록한다 (doc/26 2단계). 옷장·사진·AI 컨텍스트의 대리
 * 경로(/owners/{ownerId}/…)가 부른다. 연결은 매 요청 DB에서 확인한다 (토큰에는 넣지 않는다).
 */
public interface ClosetAccessUseCase {

  /** 보기 권한. 연결(ACTIVE)이 없으면 404 NOT_FOUND (연결 여부를 숨긴다) */
  ClosetAccessDto requireView(long helperId, long ownerId);

  /** 등록·수정 권한. 연결이 없으면 404 NOT_FOUND, 보기 권한뿐이면 403 FORBIDDEN */
  ClosetAccessDto requireEdit(long helperId, long ownerId);

  /** 대리 작업을 남긴다. 부르는 쪽의 트랜잭션에 함께 들어간다 (옷 저장이 실패하면 기록도 남지 않는다) */
  void record(ClosetAccessDto access, ProxyAction action, long itemId, String itemName);
}
