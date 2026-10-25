package app.wearly.user.application.port.in.dto;

/**
 * 탈퇴 예약 삭제 한 번의 결과 (doc/27 2단계).
 *
 * @param due 기한이 지난 대상 수 (이번에 본 것)
 * @param purged 지운 회원 수
 * @param skipped 그새 취소됐거나 다른 서버가 먼저 지워 건너뛴 수
 * @param failed 지우다 실패한 수 (다음에 다시 한다)
 */
public record WithdrawalPurgeRunDto(int due, int purged, int skipped, int failed) {}
