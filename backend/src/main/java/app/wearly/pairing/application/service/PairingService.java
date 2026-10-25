package app.wearly.pairing.application.service;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.pairing.application.port.in.PairingUseCase;
import app.wearly.pairing.application.port.in.dto.JoinPairingDto;
import app.wearly.pairing.application.port.in.dto.PairingCodeDto;
import app.wearly.pairing.application.port.in.dto.PairingDto;
import app.wearly.pairing.application.port.in.dto.PairingListDto;
import app.wearly.pairing.application.port.in.dto.ProxyActionDto;
import app.wearly.pairing.application.port.in.dto.UpdatePairingDto;
import app.wearly.pairing.application.port.out.PairingAttemptPort;
import app.wearly.pairing.application.port.out.PairingCodePort;
import app.wearly.pairing.application.port.out.PairingNoticePort;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.application.port.out.PairingQueryPort;
import app.wearly.pairing.application.port.out.dto.PairingRowDto;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingCode;
import app.wearly.pairing.domain.PairingEndReason;
import app.wearly.pairing.domain.PairingNotices;
import app.wearly.pairing.domain.PairingPermission;
import app.wearly.pairing.domain.PairingRole;
import app.wearly.pairing.domain.PairingSettings;
import app.wearly.pairing.domain.PairingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옷장 도우미 연결 (doc/26 1단계): 코드는 Valkey, 연결은 JPA, 목록·인원 수는 MyBatis */
@Service
public class PairingService implements PairingUseCase {

  /** 코드가 다른 코드와 겹칠 때 다시 만드는 횟수 (31^8가지라 겹칠 일은 거의 없다) */
  static final int MAX_ATTEMPTS = 5;

  /** 대리 작업 기록을 한 번에 보여 주는 수 */
  static final int ACTIONS_LIMIT = 100;

  private final PairingPort pairings;
  private final PairingQueryPort queries;
  private final PairingCodePort codes;
  private final PairingAttemptPort attempts;
  private final PairingNoticePort notices;
  private final PairingSettings settings;
  private final Clock clock;

  public PairingService(
      PairingPort pairings,
      PairingQueryPort queries,
      PairingCodePort codes,
      PairingAttemptPort attempts,
      PairingNoticePort notices,
      PairingSettings settings,
      Clock clock) {
    this.pairings = pairings;
    this.queries = queries;
    this.codes = codes;
    this.attempts = attempts;
    this.notices = notices;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  public PairingCodeDto issueCode(long userId, String role) {
    PairingRole r = parseIssuerRole(role);
    Instant now = clock.instant();
    PairingCode value = new PairingCode(userId, r);
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String code = PairingCode.generate();
      if (codes.issue(code, value, settings.codeTtl())) {
        return new PairingCodeDto(code, r.name(), now.plus(settings.codeTtl()));
      }
    }
    throw new IllegalStateException("연결 코드를 만들지 못했어요: user " + userId);
  }

  @Override
  @Transactional
  public PairingDto join(long userId, JoinPairingDto input) {
    String code = PairingCode.normalize(input.code());
    if (code.isEmpty()) {
      throw BusinessException.validation("code", FieldErrorCode.REQUIRED);
    }
    if (!attempts.tryAcquire(userId, settings.joinAttemptLimit(), settings.joinAttemptWindow())) {
      throw new BusinessException(ErrorCode.PAIRING_TOO_MANY_ATTEMPTS);
    }
    PairingCode found =
        (PairingCode.isFormat(code) ? codes.peek(code) : Optional.<PairingCode>empty())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_CODE_INVALID));
    // #18 전에 만든 돕는 사람 코드(최대 24시간 남는다)는 없는 코드로 본다
    if (found.issuerRole() != PairingRole.OWNER) {
      throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
    }
    if (found.issuerId() == userId) {
      throw new BusinessException(ErrorCode.PAIRING_SELF);
    }
    // 아래 확인에 걸리면 코드를 쓰지 않은 것으로 두어 다시 입력할 수 있게 한다
    long ownerId = found.issuerId();
    long helperId = userId;
    final String issuerName =
        queries
            .findUserName(found.issuerId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_CODE_INVALID));
    Instant now = clock.instant();
    Optional<Pairing> unended = pairings.findUnended(ownerId, helperId);
    if (unended.isPresent()) {
      if (!unended.get().isPendingExpired(now, settings.pendingTtl())) {
        throw new BusinessException(ErrorCode.PAIRING_EXISTS);
      }
      pairings.save(unended.get().end(PairingEndReason.EXPIRED, now));
    }
    checkLimits(ownerId, helperId, now);
    if (!codes.take(code, found)) {
      throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
    }
    Pairing created = pairings.save(Pairing.requested(ownerId, helperId, now));
    notices.sendAfterCommit(
        ownerId, created.id(), PairingNotices.requested(queries.findUserName(userId).orElse("")));
    return toDto(created, userId, issuerName);
  }

  private void checkLimits(long ownerId, long helperId, Instant now) {
    Instant pendingSince = now.minus(settings.pendingTtl());
    if (queries.countOpenAsOwner(ownerId, pendingSince) >= settings.maxHelpersPerOwner()) {
      throw new BusinessException(
          ErrorCode.PAIRING_LIMIT,
          Map.of("side", PairingRole.OWNER.name(), "max", settings.maxHelpersPerOwner()));
    }
    if (queries.countOpenAsHelper(helperId, pendingSince) >= settings.maxClosetsPerHelper()) {
      throw new BusinessException(
          ErrorCode.PAIRING_LIMIT,
          Map.of("side", PairingRole.HELPER.name(), "max", settings.maxClosetsPerHelper()));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PairingListDto list(long userId) {
    List<PairingDto> asOwner = new ArrayList<>();
    List<PairingDto> asHelper = new ArrayList<>();
    for (PairingRowDto r : queries.findOpen(userId, clock.instant().minus(settings.pendingTtl()))) {
      if (r.ownerId() == userId) {
        asOwner.add(toDto(r, PairingRole.OWNER));
      }
      if (r.helperId() == userId) {
        asHelper.add(toDto(r, PairingRole.HELPER));
      }
    }
    return new PairingListDto(asOwner, asHelper);
  }

  @Override
  @Transactional
  public PairingDto approve(long userId, long pairingId, String permission) {
    Pairing p = findOpen(userId, pairingId, PairingRole.OWNER);
    if (p.status() != PairingStatus.PENDING) {
      throw BusinessException.notFound();
    }
    PairingPermission perm = parsePermission(permission);
    Pairing approved = pairings.save(p.approve(perm, clock.instant()));
    notices.sendAfterCommit(
        p.helperId(),
        approved.id(),
        PairingNotices.accepted(queries.findUserName(userId).orElse("")));
    return toDto(approved, userId);
  }

  @Override
  @Transactional
  public void reject(long userId, long pairingId) {
    Pairing p = findOpen(userId, pairingId, PairingRole.OWNER);
    if (p.status() != PairingStatus.PENDING) {
      throw BusinessException.notFound();
    }
    pairings.save(p.end(PairingEndReason.OWNER, clock.instant()));
    notices.sendAfterCommit(
        p.helperId(), p.id(), PairingNotices.rejected(queries.findUserName(userId).orElse("")));
  }

  @Override
  @Transactional
  public PairingDto update(long userId, long pairingId, UpdatePairingDto input) {
    Pairing p = findOpen(userId, pairingId, PairingRole.OWNER);
    if (input.permission() != null) {
      if (p.status() != PairingStatus.ACTIVE) {
        throw BusinessException.notFound();
      }
      p = p.withPermission(parsePermission(input.permission()));
    }
    if (input.notifyOwner() != null) {
      p = p.withNotifyOwner(input.notifyOwner());
    }
    return toDto(pairings.save(p), userId);
  }

  @Override
  @Transactional
  public void end(long userId, long pairingId) {
    Pairing p = findOpen(userId, pairingId, null);
    pairings.save(p.end(PairingEndReason.by(p.roleOf(userId)), clock.instant()));
    // 연결된 뒤의 해제만 상대에게 알린다 (대기 요청 취소는 알리지 않는다)
    if (p.status() == PairingStatus.ACTIVE) {
      notices.sendAfterCommit(
          p.partnerOf(userId),
          p.id(),
          PairingNotices.ended(queries.findUserName(userId).orElse("")));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProxyActionDto> actions(long userId, long pairingId) {
    Pairing p = pairings.find(pairingId).orElseThrow(BusinessException::notFound);
    if (p.roleOf(userId) == null) {
      throw BusinessException.notFound();
    }
    return queries.findActions(pairingId, ACTIONS_LIMIT).stream()
        .map(
            r ->
                new ProxyActionDto(
                    r.actionId(), r.action(), r.itemId(), r.itemName(), r.createdAt()))
        .toList();
  }

  /** 이 회원이 role 자리(null이면 아무 자리)로 있는 살아 있는 연결. 아니면 404 */
  private Pairing findOpen(long userId, long pairingId, PairingRole role) {
    Pairing p = pairings.find(pairingId).orElseThrow(BusinessException::notFound);
    PairingRole mine = p.roleOf(userId);
    if (mine == null
        || (role != null && mine != role)
        || !p.isOpen(clock.instant(), settings.pendingTtl())) {
      throw BusinessException.notFound();
    }
    return p;
  }

  private PairingDto toDto(Pairing p, long viewerId) {
    String partnerName = queries.findUserName(p.partnerOf(viewerId)).orElse(null);
    return toDto(p, viewerId, partnerName);
  }

  private static PairingDto toDto(Pairing p, long viewerId, String partnerName) {
    return new PairingDto(
        p.id(),
        p.roleOf(viewerId).name(),
        p.partnerOf(viewerId),
        partnerName,
        p.permission() == null ? null : p.permission().name(),
        p.status().name(),
        p.issuedBy().name(),
        p.notifyOwner(),
        p.createdAt(),
        p.approvedAt());
  }

  private static PairingDto toDto(PairingRowDto r, PairingRole viewer) {
    boolean owner = viewer == PairingRole.OWNER;
    return new PairingDto(
        r.pairingId(),
        viewer.name(),
        owner ? r.helperId() : r.ownerId(),
        owner ? r.helperName() : r.ownerName(),
        r.permission(),
        r.status(),
        r.issuedBy(),
        r.notifyOwner(),
        r.createdAt(),
        r.approvedAt());
  }

  /** 코드는 옷장 주인만 만든다 (#18). 비우면 OWNER */
  private static PairingRole parseIssuerRole(String role) {
    if (role == null || role.isBlank()) {
      return PairingRole.OWNER;
    }
    return PairingRole.parse(role)
        .filter(r -> r == PairingRole.OWNER)
        .orElseThrow(
            () ->
                BusinessException.validation(
                    FieldError.notAllowed("role", List.of(PairingRole.OWNER.name()))));
  }

  private static PairingPermission parsePermission(String permission) {
    if (permission == null || permission.isBlank()) {
      throw BusinessException.validation("permission", FieldErrorCode.REQUIRED);
    }
    return PairingPermission.parse(permission)
        .orElseThrow(
            () ->
                BusinessException.validation(
                    FieldError.notAllowed("permission", PairingPermission.names())));
  }
}
