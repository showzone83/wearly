package app.wearly.pairing.application.port.in;

import app.wearly.pairing.application.port.in.dto.JoinPairingDto;
import app.wearly.pairing.application.port.in.dto.PairingCodeDto;
import app.wearly.pairing.application.port.in.dto.PairingDto;
import app.wearly.pairing.application.port.in.dto.PairingListDto;
import app.wearly.pairing.application.port.in.dto.ProxyActionDto;
import app.wearly.pairing.application.port.in.dto.UpdatePairingDto;
import java.util.List;

/** 옷장 도우미 연결 (doc/26 1단계): 코드 발급·입력, 승인·거절, 권한·알림 변경, 해제, 목록. 연결에 없는 회원이 다른 연결을 건드리면 404다. */
public interface PairingUseCase {

  /**
   * 옷장 주인의 연결 코드를 만든다 (#18부터 돕는 사람은 만들지 않는다). role을 비우면 OWNER. 이전 코드는 사라진다.
   *
   * <p>오류: 400 VALIDATION_FAILED (role NOT_ALLOWED: OWNER)
   */
  PairingCodeDto issueCode(long userId, String role);

  /**
   * 주인 코드를 입력한다. 입력한 회원이 돕는 사람이 되고 주인 승인을 기다린다(PENDING). #18 전에 만든 돕는 사람 코드는 없는 코드로 본다.
   *
   * <p>오류: 400 VALIDATION_FAILED (code REQUIRED) · 404 PAIRING_CODE_INVALID · 400 PAIRING_SELF ·
   * 409 PAIRING_EXISTS · 409 PAIRING_LIMIT · 429 PAIRING_TOO_MANY_ATTEMPTS
   */
  PairingDto join(long userId, JoinPairingDto input);

  /** 내 연결: 주인으로서(내 옷장을 돕는 사람)와 돕는 사람으로서(내가 돕는 옷장). 끝난 연결·만료된 대기는 뺀다 */
  PairingListDto list(long userId);

  /**
   * 주인이 대기 요청을 승인한다.
   *
   * <p>오류: 404 NOT_FOUND (주인이 아님·대기 중이 아님·만료) · 400 VALIDATION_FAILED (permission)
   */
  PairingDto approve(long userId, long pairingId, String permission);

  /** 주인이 대기 요청을 거절한다. 오류: 404 NOT_FOUND */
  void reject(long userId, long pairingId);

  /**
   * 주인이 권한·알림을 바꾼다. 권한은 연결된 뒤에만 바꿀 수 있다.
   *
   * <p>오류: 404 NOT_FOUND · 400 VALIDATION_FAILED (permission NOT_ALLOWED)
   */
  PairingDto update(long userId, long pairingId, UpdatePairingDto input);

  /** 연결을 끊는다 (양쪽 모두, 대기 요청 취소 포함). 옷은 주인 옷장에 남는다. 오류: 404 NOT_FOUND */
  void end(long userId, long pairingId);

  /**
   * 돕는 사람이 이 연결로 한 작업, 최근 순 최대 100건. 주인·돕는 사람 모두 볼 수 있고 끝난 연결도 볼 수 있다. 오류: 404 NOT_FOUND (연결에 없는
   * 회원)
   */
  List<ProxyActionDto> actions(long userId, long pairingId);

  /**
   * 탈퇴 신청 (doc/27): 이 회원이 만든 연결 코드를 지우고, 주인·돕는 사람으로 있는 끝나지 않은 연결을 모두 끝낸다(끝낸 쪽 WITHDRAWN). 연결된 것만
   * 상대에게 알린다(커밋 뒤). 옷은 주인 옷장에 남는다. 탈퇴를 취소해도 되살리지 않는다.
   */
  void endAllOnWithdrawal(long userId);
}
