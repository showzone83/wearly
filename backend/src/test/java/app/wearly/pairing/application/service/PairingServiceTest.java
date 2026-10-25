package app.wearly.pairing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.pairing.application.port.in.dto.JoinPairingDto;
import app.wearly.pairing.application.port.in.dto.PairingDto;
import app.wearly.pairing.application.port.in.dto.PairingListDto;
import app.wearly.pairing.application.port.in.dto.UpdatePairingDto;
import app.wearly.pairing.application.port.out.PairingAttemptPort;
import app.wearly.pairing.application.port.out.PairingCodePort;
import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.application.port.out.PairingQueryPort;
import app.wearly.pairing.application.port.out.dto.PairingRowDto;
import app.wearly.pairing.application.port.out.dto.ProxyActionRowDto;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingCode;
import app.wearly.pairing.domain.PairingEndReason;
import app.wearly.pairing.domain.PairingSettings;
import app.wearly.pairing.domain.PairingStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class PairingServiceTest {

  static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");
  static final long OWNER = 1L;
  static final long HELPER = 2L;
  static final long OTHER = 3L;

  /** 지금 시각 (테스트가 옮긴다) */
  Instant now = NOW;

  final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };

  final Map<Long, String> names = new HashMap<>(Map.of(1L, "엄마", 2L, "딸", 3L, "친구", 4L, "아빠"));

  final Map<Long, Pairing> rows = new LinkedHashMap<>();
  long nextId = 100;

  final PairingPort pairings =
      new PairingPort() {
        @Override
        public Optional<Pairing> find(long pairingId) {
          return Optional.ofNullable(rows.get(pairingId));
        }

        @Override
        public Optional<Pairing> findUnended(long ownerId, long helperId) {
          return rows.values().stream()
              .filter(p -> p.ownerId() == ownerId && p.helperId() == helperId)
              .filter(p -> p.status() != PairingStatus.ENDED)
              .findFirst();
        }

        @Override
        public Pairing save(Pairing p) {
          Pairing saved =
              p.id() != null
                  ? p
                  : new Pairing(
                      nextId++,
                      p.ownerId(),
                      p.helperId(),
                      p.permission(),
                      p.status(),
                      p.issuedBy(),
                      p.notifyOwner(),
                      p.createdAt(),
                      p.approvedAt(),
                      p.endedAt(),
                      p.endedBy());
          rows.put(saved.id(), saved);
          return saved;
        }
      };

  final PairingQueryPort queries =
      new PairingQueryPort() {
        boolean open(Pairing p, Instant pendingSince) {
          return p.status() == PairingStatus.ACTIVE
              || (p.status() == PairingStatus.PENDING && p.createdAt().isAfter(pendingSince));
        }

        @Override
        public long countOpenAsOwner(long ownerId, Instant pendingSince) {
          return rows.values().stream()
              .filter(p -> p.ownerId() == ownerId && open(p, pendingSince))
              .count();
        }

        @Override
        public long countOpenAsHelper(long helperId, Instant pendingSince) {
          return rows.values().stream()
              .filter(p -> p.helperId() == helperId && open(p, pendingSince))
              .count();
        }

        @Override
        public List<PairingRowDto> findOpen(long userId, Instant pendingSince) {
          return rows.values().stream()
              .filter(p -> p.ownerId() == userId || p.helperId() == userId)
              .filter(p -> open(p, pendingSince))
              .sorted(Comparator.comparing(Pairing::createdAt).reversed())
              .map(
                  p ->
                      new PairingRowDto(
                          p.id(),
                          p.ownerId(),
                          names.get(p.ownerId()),
                          p.helperId(),
                          names.get(p.helperId()),
                          p.permission() == null ? null : p.permission().name(),
                          p.status().name(),
                          p.issuedBy().name(),
                          p.notifyOwner(),
                          p.createdAt(),
                          p.approvedAt()))
              .toList();
        }

        @Override
        public Optional<String> findUserName(long userId) {
          return Optional.ofNullable(names.get(userId));
        }

        @Override
        public List<ProxyActionRowDto> findActions(long pairingId, int limit) {
          return actionRows.stream().filter(r -> r.actionId() == pairingId).limit(limit).toList();
        }
      };

  /** 대리 작업 기록 (테스트에서는 actionId 자리에 연결 ID를 넣어 걸러 쓴다) */
  final List<ProxyActionRowDto> actionRows = new ArrayList<>();

  /** 코드 → 값 (만료는 테스트하지 않는다: Valkey ttl) */
  final Map<String, PairingCode> codeRows = new HashMap<>();

  final PairingCodePort codes =
      new PairingCodePort() {
        @Override
        public boolean issue(String code, PairingCode value, Duration ttl) {
          if (codeRows.containsKey(code)) {
            return false;
          }
          codeRows.values().removeIf(v -> v.equals(value));
          codeRows.put(code, value);
          return true;
        }

        @Override
        public Optional<PairingCode> peek(String code) {
          return Optional.ofNullable(codeRows.get(code));
        }

        @Override
        public boolean take(String code, PairingCode value) {
          return codeRows.remove(code, value);
        }
      };

  int attemptCount;

  final PairingAttemptPort attempts = (userId, limit, window) -> ++attemptCount <= limit;

  final PairingSettings settings =
      new PairingSettings(1, 2, Duration.ofHours(24), Duration.ofDays(7), 10, Duration.ofHours(1));

  final PairingService service =
      new PairingService(pairings, queries, codes, attempts, settings, clock);

  String codeOf(long userId, String role) {
    return service.issueCode(userId, role).code();
  }

  static void assertError(ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void 주인_코드를_돕는_사람이_넣으면_대기하고_주인이_승인한다() {
    String code = codeOf(OWNER, "owner");
    PairingDto joined = service.join(HELPER, new JoinPairingDto(code.toLowerCase(), "EDIT"));

    assertThat(joined.status()).isEqualTo("PENDING");
    assertThat(joined.role()).isEqualTo("HELPER");
    assertThat(joined.partnerName()).isEqualTo("엄마");
    assertThat(joined.permission()).isNull(); // 돕는 사람이 보낸 권한은 무시
    assertThat(codeRows).isEmpty(); // 1회용

    PairingDto approved = service.approve(OWNER, joined.id(), "view");
    assertThat(approved.status()).isEqualTo("ACTIVE");
    assertThat(approved.role()).isEqualTo("OWNER");
    assertThat(approved.partnerName()).isEqualTo("딸");
    assertThat(approved.permission()).isEqualTo("VIEW");
    assertThat(approved.approvedAt()).isEqualTo(NOW);
  }

  @Test
  void 돕는_사람_코드를_주인이_넣으면_입력이_곧_승인() {
    String code = codeOf(HELPER, "HELPER");
    PairingDto joined = service.join(OWNER, new JoinPairingDto(code, "EDIT"));

    assertThat(joined.status()).isEqualTo("ACTIVE");
    assertThat(joined.role()).isEqualTo("OWNER");
    assertThat(joined.permission()).isEqualTo("EDIT");
    assertThat(joined.issuedBy()).isEqualTo("HELPER");
  }

  @Test
  void 돕는_사람_코드를_넣을_때는_권한이_필요하고_틀리면_코드가_남는다() {
    String code = codeOf(HELPER, "HELPER");
    assertError(
        () -> service.join(OWNER, new JoinPairingDto(code, null)), ErrorCode.VALIDATION_FAILED);
    assertError(
        () -> service.join(OWNER, new JoinPairingDto(code, "PLAN")), ErrorCode.VALIDATION_FAILED);
    assertThat(codeRows).containsKey(code);
  }

  @Test
  void 자리는_OWNER_HELPER만() {
    assertError(() -> service.issueCode(OWNER, "FRIEND"), ErrorCode.VALIDATION_FAILED);
    assertError(() -> service.issueCode(OWNER, " "), ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void 새_코드를_만들면_같은_자리의_이전_코드는_사라진다() {
    String first = codeOf(OWNER, "OWNER");
    String second = codeOf(OWNER, "OWNER");
    String helperCode = codeOf(OWNER, "HELPER");
    assertThat(codeRows).doesNotContainKey(first).containsKeys(second, helperCode);
  }

  @Test
  void 없는_코드_형식이_틀린_코드_빈_코드() {
    assertError(
        () -> service.join(HELPER, new JoinPairingDto("ZZZZ-ZZZZ", null)),
        ErrorCode.PAIRING_CODE_INVALID);
    assertError(
        () -> service.join(HELPER, new JoinPairingDto("짧음", null)), ErrorCode.PAIRING_CODE_INVALID);
    assertError(
        () -> service.join(HELPER, new JoinPairingDto(" - ", null)), ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void 내_코드는_내가_넣을_수_없고_코드는_남는다() {
    String code = codeOf(OWNER, "OWNER");
    assertError(() -> service.join(OWNER, new JoinPairingDto(code, null)), ErrorCode.PAIRING_SELF);
    assertThat(codeRows).containsKey(code);
  }

  @Test
  void 코드를_만든_회원이_탈퇴했으면_없는_코드() {
    String code = codeOf(OWNER, "OWNER");
    names.remove(OWNER);
    assertError(
        () -> service.join(HELPER, new JoinPairingDto(code, null)), ErrorCode.PAIRING_CODE_INVALID);
  }

  @Test
  void 이미_연결됐거나_대기_중이면_PAIRING_EXISTS() {
    service.join(HELPER, new JoinPairingDto(codeOf(OWNER, "OWNER"), null));
    String again = codeOf(OWNER, "OWNER");
    assertError(
        () -> service.join(HELPER, new JoinPairingDto(again, null)), ErrorCode.PAIRING_EXISTS);
    assertThat(codeRows).containsKey(again);
  }

  @Test
  void 대기가_만료됐으면_끝내고_다시_요청할_수_있다() {
    PairingDto first = service.join(HELPER, new JoinPairingDto(codeOf(OWNER, "OWNER"), null));
    now = NOW.plus(Duration.ofDays(8));

    PairingDto second = service.join(HELPER, new JoinPairingDto(codeOf(OWNER, "OWNER"), null));
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(rows.get(first.id()).status()).isEqualTo(PairingStatus.ENDED);
    assertThat(rows.get(first.id()).endedBy()).isEqualTo(PairingEndReason.EXPIRED);
    // 만료된 대기는 승인할 수 없다
    now = NOW.plus(Duration.ofDays(16));
    assertError(() -> service.approve(OWNER, second.id(), "VIEW"), ErrorCode.NOT_FOUND);
  }

  @Test
  void 주인은_돕는_사람_1명_대기도_센다() {
    service.join(HELPER, new JoinPairingDto(codeOf(OWNER, "OWNER"), null));
    String code = codeOf(OWNER, "OWNER");
    assertThatThrownBy(() -> service.join(OTHER, new JoinPairingDto(code, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.PAIRING_LIMIT);
              assertThat(e.params()).containsEntry("side", "OWNER").containsEntry("max", 1);
            });
    assertThat(codeRows).containsKey(code);
  }

  @Test
  void 돕는_사람은_옷장_2곳까지() {
    service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    service.join(4L, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    String code = codeOf(HELPER, "HELPER");
    assertThatThrownBy(() -> service.join(OTHER, new JoinPairingDto(code, "VIEW")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.params()).containsEntry("side", "HELPER").containsEntry("max", 2));
  }

  @Test
  void 입력_시도가_한도를_넘으면_429() {
    for (int i = 0; i < settings.joinAttemptLimit(); i++) {
      assertError(
          () -> service.join(HELPER, new JoinPairingDto("ZZZZZZZZ", null)),
          ErrorCode.PAIRING_CODE_INVALID);
    }
    String code = codeOf(OWNER, "OWNER");
    assertError(
        () -> service.join(HELPER, new JoinPairingDto(code, null)),
        ErrorCode.PAIRING_TOO_MANY_ATTEMPTS);
  }

  @Test
  void 목록은_내_자리별로_나누고_끝난_연결과_만료된_대기는_뺀다() {
    final PairingDto mine =
        service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    now = NOW.plusSeconds(60);
    service.join(OWNER, new JoinPairingDto(codeOf(4L, "OWNER"), null)); // 엄마가 아빠를 돕는다 (대기)

    PairingListDto list = service.list(OWNER);
    assertThat(list.asOwner()).extracting(PairingDto::partnerName).containsExactly("딸");
    assertThat(list.asHelper()).extracting(PairingDto::partnerName).containsExactly("아빠");
    assertThat(service.list(HELPER).asHelper())
        .extracting(PairingDto::id)
        .containsExactly(mine.id());

    now = NOW.plus(Duration.ofDays(8));
    assertThat(service.list(OWNER).asHelper()).isEmpty();
    service.end(HELPER, mine.id());
    assertThat(service.list(OWNER).asOwner()).isEmpty();
  }

  @Test
  void 승인_거절_권한_변경은_주인만() {
    PairingDto p = service.join(HELPER, new JoinPairingDto(codeOf(OWNER, "OWNER"), null));
    long id = p.id();
    assertError(() -> service.approve(HELPER, id, "EDIT"), ErrorCode.NOT_FOUND);
    assertError(() -> service.reject(OTHER, id), ErrorCode.NOT_FOUND);
    assertError(
        () -> service.update(HELPER, id, new UpdatePairingDto("EDIT", null)), ErrorCode.NOT_FOUND);
    // 대기 중에는 권한을 바꿀 수 없고 알림만 바꿀 수 있다
    assertError(
        () -> service.update(OWNER, id, new UpdatePairingDto("EDIT", null)), ErrorCode.NOT_FOUND);
    assertThat(service.update(OWNER, id, new UpdatePairingDto(null, false)).notifyOwner())
        .isFalse();

    service.reject(OWNER, id);
    assertThat(rows.get(id).status()).isEqualTo(PairingStatus.ENDED);
    assertThat(rows.get(id).endedBy()).isEqualTo(PairingEndReason.OWNER);
    assertError(() -> service.approve(OWNER, id, "EDIT"), ErrorCode.NOT_FOUND);
  }

  @Test
  void 연결된_뒤_권한을_바꾸고_이미_연결됐으면_승인할_수_없다() {
    PairingDto p = service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "VIEW"));
    assertThat(service.update(OWNER, p.id(), new UpdatePairingDto("edit", null)).permission())
        .isEqualTo("EDIT");
    assertError(
        () -> service.update(OWNER, p.id(), new UpdatePairingDto("ADMIN", null)),
        ErrorCode.VALIDATION_FAILED);
    assertError(() -> service.approve(OWNER, p.id(), "EDIT"), ErrorCode.NOT_FOUND);
  }

  @Test
  void 작업_기록은_연결의_양쪽만_보고_끝난_연결도_볼_수_있다() {
    PairingDto p = service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    actionRows.add(new ProxyActionRowDto(p.id(), "ITEM_CREATE", 7L, "셔츠", NOW));

    assertThat(service.actions(OWNER, p.id())).extracting("itemName").containsExactly("셔츠");
    assertThat(service.actions(HELPER, p.id())).hasSize(1);
    assertError(() -> service.actions(OTHER, p.id()), ErrorCode.NOT_FOUND);
    assertError(() -> service.actions(OWNER, 999L), ErrorCode.NOT_FOUND);

    service.end(OWNER, p.id());
    assertThat(service.actions(HELPER, p.id())).hasSize(1);
  }

  @Test
  void 해제는_양쪽_모두_할_수_있고_다른_사람은_404() {
    PairingDto p = service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    assertError(() -> service.end(OTHER, p.id()), ErrorCode.NOT_FOUND);

    service.end(HELPER, p.id());
    assertThat(rows.get(p.id()).endedBy()).isEqualTo(PairingEndReason.HELPER);
    assertError(() -> service.end(OWNER, p.id()), ErrorCode.NOT_FOUND);

    // 끝난 뒤 다시 연결할 수 있다
    PairingDto again = service.join(OWNER, new JoinPairingDto(codeOf(HELPER, "HELPER"), "EDIT"));
    service.end(OWNER, again.id());
    assertThat(rows.get(again.id()).endedBy()).isEqualTo(PairingEndReason.OWNER);
  }
}
