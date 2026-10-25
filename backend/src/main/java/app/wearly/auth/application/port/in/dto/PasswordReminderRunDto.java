package app.wearly.auth.application.port.in.dto;

/**
 * 비밀번호 변경 알림 한 번의 결과 (28 문서 2단계).
 *
 * @param due 알림 대상 수 (이번에 본 것)
 * @param reminded 알림을 보낸다고 표시한 수 (이 주기에는 다시 보내지 않는다)
 * @param delivered 그중 공급자가 한 기기라도 받은 수 (푸시가 꺼졌거나 기기가 없으면 빠진다)
 * @param skipped 그새 바꿨거나 연장했거나 다른 서버가 먼저 잡아 건너뛴 수
 * @param failed 처리하다 실패한 수 (다음에 다시 한다)
 */
public record PasswordReminderRunDto(
    int due, int reminded, int delivered, int skipped, int failed) {}
