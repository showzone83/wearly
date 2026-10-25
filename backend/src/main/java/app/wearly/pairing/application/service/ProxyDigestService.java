package app.wearly.pairing.application.service;

import app.wearly.pairing.application.port.in.ProxyDigestUseCase;
import app.wearly.pairing.application.port.in.dto.ProxyDigestRunDto;
import app.wearly.pairing.application.port.out.PairingNoticePort;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.application.port.out.PairingQueryPort;
import app.wearly.pairing.application.port.out.ProxyDigestPort;
import app.wearly.pairing.application.port.out.dto.ClaimedActionRowDto;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingNotices;
import app.wearly.pairing.domain.PairingSettings;
import app.wearly.pairing.domain.PairingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 대리 작업 묶음 알림 (doc/26 3단계, D8). 마지막 작업 뒤 digestDelay 동안 조용하거나 첫 작업 뒤 digestMaxWait이 지난 연결의 작업을 가져오며
 * 알림 보낸 것으로 표시하고, 주인에게 한 번 알린다. 푸시는 외부 호출이라 트랜잭션을 걸지 않는다 (가져오기는 SQL 한 번이라 서버가 여러 대여도 한 번만 보낸다).
 * 보내기에 실패해도 다시 보내지 않는다 (알림은 부가 기능이고 작업 기록은 남아 있다).
 */
@Service
public class ProxyDigestService implements ProxyDigestUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProxyDigestService.class);

  private final PairingPort pairings;
  private final PairingQueryPort queries;
  private final ProxyDigestPort digest;
  private final PairingNoticePort notices;
  private final PairingSettings settings;
  private final Clock clock;

  public ProxyDigestService(
      PairingPort pairings,
      PairingQueryPort queries,
      ProxyDigestPort digest,
      PairingNoticePort notices,
      PairingSettings settings,
      Clock clock) {
    this.pairings = pairings;
    this.queries = queries;
    this.digest = digest;
    this.notices = notices;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  public ProxyDigestRunDto sendDue() {
    return sendDue(clock.instant());
  }

  @Override
  public ProxyDigestRunDto sendDue(Instant now) {
    List<Long> due =
        digest.findDue(now.minus(settings.digestDelay()), now.minus(settings.digestMaxWait()));
    int notified = 0;
    int skipped = 0;
    for (long pairingId : due) {
      try {
        List<ClaimedActionRowDto> claimed = digest.claim(pairingId, now, now);
        if (claimed.isEmpty()) {
          continue; // 다른 서버가 먼저 가져갔다
        }
        Pairing p = pairings.find(pairingId).orElse(null);
        Optional<PairingNotices> notice = noticeFor(p, claimed);
        if (notice.isEmpty()) {
          skipped++;
        } else if (notices.send(p.ownerId(), pairingId, notice.get())) {
          notified++;
        }
      } catch (RuntimeException e) {
        log.warn("묶음 알림 실패: 연결 {}", pairingId, e);
      }
    }
    if (!due.isEmpty()) {
      log.info("옷장 도우미 묶음 알림 ({} 기준): 대상 {}, 알림 {}, 건너뜀 {}", now, due.size(), notified, skipped);
    }
    return new ProxyDigestRunDto(due.size(), notified, skipped);
  }

  /** 알릴 문구. 연결이 끝났거나 주인이 알림을 껐으면 없다 */
  private Optional<PairingNotices> noticeFor(Pairing p, List<ClaimedActionRowDto> claimed) {
    if (p == null || p.status() != PairingStatus.ACTIVE || !p.notifyOwner()) {
      return Optional.empty();
    }
    Set<Long> created = new HashSet<>();
    Set<Long> updated = new HashSet<>();
    Set<Long> retired = new HashSet<>();
    Set<Long> restored = new HashSet<>();
    for (ClaimedActionRowDto c : claimed) {
      switch (c.action()) {
        case "ITEM_CREATE" -> created.add(c.itemId());
        case "ITEM_UPDATE" -> updated.add(c.itemId());
        case "ITEM_RETIRE" -> retired.add(c.itemId());
        case "ITEM_RESTORE" -> restored.add(c.itemId());
        default -> log.warn("모르는 대리 작업 종류: {}", c.action());
      }
    }
    String helperName = queries.findUserName(p.helperId()).orElse("돕는 사람");
    return Optional.ofNullable(
        PairingNotices.digest(
            helperName, created.size(), updated.size(), retired.size(), restored.size()));
  }
}
