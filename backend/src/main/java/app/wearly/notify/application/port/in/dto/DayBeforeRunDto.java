package app.wearly.notify.application.port.in.dto;

import java.time.LocalDate;

/**
 * D-1 작업 한 번의 결과 (로그·테스트용).
 *
 * @param planDate 알린 예정 날짜 (내일)
 * @param plans 그 날짜의 예정 수 (모든 회원)
 * @param notified 보낸 예정 수 (기기 하나라도 공급자가 받은 것)
 * @param changed 그중 "예보가 바뀌었어요"로 보낸 예정 수
 * @param alreadySent 오늘 이미 보내서 건너뛴 예정 수
 * @param skipped 알림을 끈 회원·토큰이 없는 회원의 예정 수
 * @param failed 모든 기기에 보내지 못한 예정 수 (기록을 풀어 같은 날 다시 실행하면 다시 보낸다)
 * @param messages 보낸 푸시 건수 (기기 수)
 * @param removedTokens DeviceNotRegistered로 지운 토큰 수
 * @param disabled 서버 푸시가 꺼져 있어 아무것도 하지 않았다
 */
public record DayBeforeRunDto(
    LocalDate planDate,
    int plans,
    int notified,
    int changed,
    int alreadySent,
    int skipped,
    int failed,
    int messages,
    int removedTokens,
    boolean disabled) {

  public static DayBeforeRunDto off(LocalDate planDate) {
    return new DayBeforeRunDto(planDate, 0, 0, 0, 0, 0, 0, 0, 0, true);
  }
}
