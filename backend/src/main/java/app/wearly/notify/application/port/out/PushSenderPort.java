package app.wearly.notify.application.port.out;

import java.util.List;

/** 푸시 보내기 (Expo Push API 또는 로그만 남기는 가짜). throw하지 않고 건마다 결과를 돌려준다 */
public interface PushSenderPort {

  /** 결과는 messages와 같은 순서·같은 개수다 */
  List<PushResult> send(List<PushMessage> messages);
}
