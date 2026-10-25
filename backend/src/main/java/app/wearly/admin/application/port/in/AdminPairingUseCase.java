package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminPairingActionDto;
import app.wearly.admin.application.port.in.dto.AdminPairingDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import java.util.List;

/** 옷장 도우미 연결 관리 (doc/26 6단계, 07 문서 4장 "옷장 도우미"): 목록·작업 기록 읽기, 강제 해제 */
public interface AdminPairingUseCase {

  /**
   * 연결 목록, 최근 요청순. q: 주인·돕는 사람 이메일·이름 부분 일치. status: PENDING · ACTIVE · ENDED.
   *
   * <p>오류: 400 VALIDATION_FAILED (status NOT_ALLOWED, page·size)
   */
  PageDto<AdminPairingDto> list(String q, String status, PagingDto paging);

  /** 연결의 대리 작업 기록 (최신순 최대 100건). 오류: 404 NOT_FOUND */
  List<AdminPairingActionDto> actions(long pairingId);

  /** 연결을 강제로 끝내고 변경 이력(PAIRING_FORCE_END)을 남긴다 (같은 트랜잭션). 오류: 404 NOT_FOUND (없거나 이미 끝남) */
  void forceEnd(long adminId, long pairingId);
}
