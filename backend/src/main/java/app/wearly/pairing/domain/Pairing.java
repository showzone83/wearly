package app.wearly.pairing.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 옷장 주인과 돕는 사람의 연결 (tb_closet_pairing, doc/26). 상태를 바꾸는 메서드는 새 값을 돌려준다.
 *
 * @param id 저장 전에는 null
 * @param permission 승인 전(PENDING)에는 null
 * @param issuedBy 연결 코드를 만든 쪽
 * @param notifyOwner 돕는 사람의 작업을 주인에게 알릴지
 * @param endedBy 끝나지 않았으면 null
 */
public record Pairing(
    Long id,
    long ownerId,
    long helperId,
    PairingPermission permission,
    PairingStatus status,
    PairingRole issuedBy,
    boolean notifyOwner,
    Instant createdAt,
    Instant approvedAt,
    Instant endedAt,
    PairingEndReason endedBy) {

  /** 돕는 사람이 주인 코드를 입력했다: 주인 승인을 기다린다 */
  public static Pairing requested(long ownerId, long helperId, Instant now) {
    return new Pairing(
        null,
        ownerId,
        helperId,
        null,
        PairingStatus.PENDING,
        PairingRole.OWNER,
        true,
        now,
        null,
        null,
        null);
  }

  /** 주인이 돕는 사람 코드를 입력했다: 입력이 곧 승인이다 */
  public static Pairing accepted(
      long ownerId, long helperId, PairingPermission permission, Instant now) {
    return new Pairing(
        null,
        ownerId,
        helperId,
        permission,
        PairingStatus.ACTIVE,
        PairingRole.HELPER,
        true,
        now,
        now,
        null,
        null);
  }

  /** 이 회원의 자리. 연결에 없는 회원이면 null */
  public PairingRole roleOf(long userId) {
    if (userId == ownerId) {
      return PairingRole.OWNER;
    }
    if (userId == helperId) {
      return PairingRole.HELPER;
    }
    return null;
  }

  public long partnerOf(long userId) {
    return userId == ownerId ? helperId : ownerId;
  }

  /** 승인을 기다리다 pendingTtl이 지났는지 */
  public boolean isPendingExpired(Instant now, Duration pendingTtl) {
    return status == PairingStatus.PENDING && !createdAt.plus(pendingTtl).isAfter(now);
  }

  /** 아직 살아 있는 연결인지 (연결됨 또는 만료 전 대기) */
  public boolean isOpen(Instant now, Duration pendingTtl) {
    return status == PairingStatus.ACTIVE
        || (status == PairingStatus.PENDING && !isPendingExpired(now, pendingTtl));
  }

  public Pairing approve(PairingPermission p, Instant now) {
    return new Pairing(
        id,
        ownerId,
        helperId,
        p,
        PairingStatus.ACTIVE,
        issuedBy,
        notifyOwner,
        createdAt,
        now,
        null,
        null);
  }

  public Pairing withPermission(PairingPermission p) {
    return new Pairing(
        id,
        ownerId,
        helperId,
        p,
        status,
        issuedBy,
        notifyOwner,
        createdAt,
        approvedAt,
        endedAt,
        endedBy);
  }

  public Pairing withNotifyOwner(boolean notify) {
    return new Pairing(
        id,
        ownerId,
        helperId,
        permission,
        status,
        issuedBy,
        notify,
        createdAt,
        approvedAt,
        endedAt,
        endedBy);
  }

  public Pairing end(PairingEndReason reason, Instant now) {
    return new Pairing(
        id,
        ownerId,
        helperId,
        permission,
        PairingStatus.ENDED,
        issuedBy,
        notifyOwner,
        createdAt,
        approvedAt,
        now,
        reason);
  }
}
