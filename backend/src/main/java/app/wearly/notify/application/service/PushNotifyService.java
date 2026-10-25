package app.wearly.notify.application.service;

import app.wearly.notify.application.port.in.PushNotifyUseCase;
import app.wearly.notify.application.port.in.dto.PushNoticeDto;
import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import app.wearly.notify.application.port.out.PushSenderPort;
import app.wearly.notify.application.port.out.PushTokenPort;
import app.wearly.notify.application.port.out.PushTokenQueryPort;
import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import app.wearly.notify.domain.PushSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 일반 알림 보내기. 외부 호출이라 트랜잭션을 걸지 않는다 (토큰 조회·삭제는 각자 짧은 트랜잭션) */
@Service
public class PushNotifyService implements PushNotifyUseCase {

  /** 일반 알림은 하루 안에 전달하지 못하면 의미가 없다 */
  static final int TTL_SECONDS = 24 * 60 * 60;

  private static final Logger log = LoggerFactory.getLogger(PushNotifyService.class);

  private final PushTokenQueryPort tokenQueries;
  private final PushTokenPort tokens;
  private final PushSenderPort sender;
  private final PushSettings settings;

  public PushNotifyService(
      PushTokenQueryPort tokenQueries,
      PushTokenPort tokens,
      PushSenderPort sender,
      PushSettings settings) {
    this.tokenQueries = tokenQueries;
    this.tokens = tokens;
    this.sender = sender;
    this.settings = settings;
  }

  @Override
  public int notifyUser(long userId, PushNoticeDto notice) {
    if (!settings.enabled()) {
      return 0;
    }
    try {
      List<PushTargetRowDto> devices = tokenQueries.findByUsers(List.of(userId));
      if (devices.isEmpty()) {
        return 0;
      }
      List<PushMessage> messages = new ArrayList<>();
      for (PushTargetRowDto d : devices) {
        messages.add(
            new PushMessage(
                d.token(),
                notice.title(),
                notice.body(),
                notice.data(),
                notice.channelId(),
                TTL_SECONDS));
      }
      List<PushResult> results = sender.send(messages);
      int accepted = 0;
      Set<String> gone = new LinkedHashSet<>();
      for (int i = 0; i < messages.size(); i++) {
        PushResult r = i < results.size() ? results.get(i) : PushResult.failed("NO_RESULT");
        if (r.status() == PushResult.Status.OK) {
          accepted++;
        } else if (r.status() == PushResult.Status.DEVICE_NOT_REGISTERED) {
          gone.add(messages.get(i).token());
        }
      }
      if (!gone.isEmpty()) {
        tokens.deleteTokens(gone);
      }
      return accepted;
    } catch (RuntimeException e) {
      log.warn("알림 보내기 실패: user {}", userId, e);
      return 0;
    }
  }
}
