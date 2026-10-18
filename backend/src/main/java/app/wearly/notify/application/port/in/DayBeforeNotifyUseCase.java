package app.wearly.notify.application.port.in;

import app.wearly.notify.application.port.in.dto.DayBeforeRunDto;
import java.time.LocalDate;

/**
 * 예정 코디 D-1 알림 (서버 푸시). 내일(한국 날짜) 예정이 있고 예정 전날 알림을 켠 회원의 기기마다 보낸다. 장소 날씨를 다시 확인해 저장된 값과 크게 다르면(기온
 * 3도 이상·비 여부) "예보가 바뀌었어요" 문구로 보낸다. 같은 예정은 하루에 한 번만 보낸다.
 */
public interface DayBeforeNotifyUseCase {

  /** 오늘(한국 날짜) 기준으로 실행한다. wearly.notify.push.enabled=false면 아무것도 하지 않는다 */
  DayBeforeRunDto runDayBefore();

  /** 이 날을 오늘로 보고 실행한다 (내일 = today + 1일) */
  DayBeforeRunDto runDayBefore(LocalDate today);
}
