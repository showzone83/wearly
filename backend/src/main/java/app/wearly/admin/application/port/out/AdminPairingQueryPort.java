package app.wearly.admin.application.port.out;

import app.wearly.admin.application.port.out.dto.AdminPairingActionRowDto;
import app.wearly.admin.application.port.out.dto.AdminPairingRowDto;
import java.util.List;

/** 옷장 도우미 연결·대리 작업 기록 읽기 (MyBatis, tb_closet_pairing · tb_closet_proxy_action과 회원) */
public interface AdminPairingQueryPort {

  List<AdminPairingRowDto> findPairings(PairingFilter filter, int limit, long offset);

  long countPairings(PairingFilter filter);

  /** 연결 하나 (없으면 null) */
  AdminPairingRowDto findPairing(long pairingId);

  /** 연결의 대리 작업 기록, 최신순 */
  List<AdminPairingActionRowDto> findActions(long pairingId, int limit);

  /** query: 주인·돕는 사람 이메일·이름 부분 일치. status: PENDING · ACTIVE · ENDED (null이면 모두) */
  record PairingFilter(String query, String status) {}
}
