package app.wearly.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.auth.application.port.in.dto.PasswordReminderRunDto;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.domain.LocalCredential;
import app.wearly.auth.domain.PasswordChangePolicy;
import app.wearly.auth.domain.PasswordReminderNotice;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 비밀번호 변경 알림 작업 (28 문서 2단계): 처리권을 잡은 회원에게만 보내고, 한 명이 실패해도 계속한다 (가짜 포트) */
class PasswordReminderServiceTest {

  static final Instant NOW = Instant.parse("2026-10-25T01:00:00Z");

  final List<Long> due = new ArrayList<>(List.of(1L, 2L, 3L, 4L));

  /** 그새 바꾸거나 다른 서버가 잡은 회원 */
  final Set<Long> lost = Set.of(2L);

  /** 처리권을 잡다 실패하는 회원 */
  final Set<Long> broken = Set.of(3L);

  /** 기기가 없는 회원 */
  final Set<Long> noDevice = Set.of(4L);

  final List<Instant> dueByAsked = new ArrayList<>();
  final List<Long> sent = new ArrayList<>();
  final List<PasswordReminderNotice> notices = new ArrayList<>();

  final CredentialPort credentials =
      new CredentialPort() {
        @Override
        public List<Long> findReminderDueUserIds(Instant dueBy, int limit) {
          dueByAsked.add(dueBy);
          return due.subList(0, Math.min(limit, due.size()));
        }

        @Override
        public boolean claimReminder(long userId, Instant dueBy, Instant now) {
          if (broken.contains(userId)) {
            throw new IllegalStateException("DB 오류");
          }
          return !lost.contains(userId);
        }

        @Override
        public Optional<LocalCredential> findByEmail(String email) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByEmail(String email) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LocalCredential> findByUserId(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void create(LocalCredential credential) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void markLogin(long credentialId, Instant at) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void changePassword(long credentialId, String passwordHash, Instant at) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void postpone(long credentialId, Instant at) {
          throw new UnsupportedOperationException();
        }
      };

  PasswordReminderService service(Duration interval, int batch) {
    return new PasswordReminderService(
        credentials,
        (userId, notice) -> {
          sent.add(userId);
          notices.add(notice);
          return !noDevice.contains(userId);
        },
        new PasswordChangePolicy(interval, batch),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void 처리권을_잡은_회원에게만_보내고_실패해도_계속한다() {
    PasswordReminderRunDto run = service(Duration.ofDays(90), 500).remindDue();

    assertThat(run).isEqualTo(new PasswordReminderRunDto(4, 2, 1, 1, 1));
    assertThat(sent).containsExactly(1L, 4L);
    assertThat(dueByAsked).containsExactly(NOW.minus(Duration.ofDays(90)));
    assertThat(notices.get(0).body()).contains("90일");
    assertThat(notices.get(0).data()).containsEntry("type", PasswordReminderNotice.TYPE);
  }

  @Test
  void 한_번에_정해진_수까지만_본다() {
    assertThat(service(Duration.ofDays(90), 2).remindDue().due()).isEqualTo(2);
  }

  @Test
  void 기능이_꺼졌으면_아무것도_하지_않는다() {
    assertThat(service(Duration.ZERO, 500).remindDue())
        .isEqualTo(new PasswordReminderRunDto(0, 0, 0, 0, 0));
    assertThat(dueByAsked).isEmpty();
    assertThat(sent).isEmpty();
  }
}
