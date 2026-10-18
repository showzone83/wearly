package app.wearly.notify.adapter.out.push;

import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import app.wearly.notify.application.port.out.PushSenderPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로그만 남기는 가짜 푸시 (wearly.notify.push.provider=log, 로컬 기본). 실제로 보내지 않고 모두 성공으로 답한다. 토큰·문구는 로그에 남기지 않고
 * 건수와 data.type·예정 ID만 남긴다.
 */
public final class LogPushSenderAdapter implements PushSenderPort {

  private static final Logger log = LoggerFactory.getLogger(LogPushSenderAdapter.class);

  @Override
  public List<PushResult> send(List<PushMessage> messages) {
    List<String> plans = new ArrayList<>();
    for (PushMessage m : messages) {
      if (m.data() != null && m.data().get("serverId") != null) {
        plans.add(m.data().get("serverId"));
      }
    }
    log.info("[push:log] 보내지 않고 기록만 해요: {}건, 예정 {}", messages.size(), plans);
    return new ArrayList<>(Collections.nCopies(messages.size(), PushResult.ok()));
  }
}
