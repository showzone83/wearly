package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.EnsureAdminUseCase;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장, 22 문서 4.1). 관리자가 한 명도 없을 때만 설정 계정을 만들고(ADMIN_BOOTSTRAP_CREATE), 같은
 * 이메일로 먼저 가입한 일반 회원은 관리자로 올리지 않는다 — 공개 가입 API로 관리자 이메일을 선점해 관리자가 되는 것을 막는다.
 */
@Service
public class AdminAccountService implements EnsureAdminUseCase {

  private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

  static final String DEFAULT_NAME = "관리자";

  private final AccountUseCase accounts;
  private final UserUseCase users;
  private final RecordAuditPort audit;

  public AdminAccountService(AccountUseCase accounts, UserUseCase users, RecordAuditPort audit) {
    this.accounts = accounts;
    this.users = users;
    this.audit = audit;
  }

  @Override
  @Transactional
  public Optional<Long> ensureAdmin(String rawEmail, String password, String rawName) {
    long admins = users.countAdmins();
    if (admins > 0) {
      log.info("관리자 계정: 관리자가 이미 {}명 있어 설정 계정(wearly.admin.email)은 쓰지 않아요", admins);
      return Optional.empty();
    }
    String email = rawEmail.strip().toLowerCase(Locale.ROOT);
    if (accounts.findUserIdByEmail(email).isPresent()) {
      log.warn(
          "관리자 계정: 관리자가 없지만 설정 이메일({})로 이미 가입한 일반 회원이 있어 관리자로 올리지 않아요. "
              + "그 회원이 본인이면 DB에서 직접 역할을 바꾸거나, 설정에 다른 이메일을 넣고 다시 시작하세요.",
          mask(email));
      return Optional.empty();
    }
    String name = rawName == null || rawName.isBlank() ? DEFAULT_NAME : rawName.strip();
    if (name.length() > 20) {
      name = name.substring(0, 20);
    }
    long id = accounts.createAccount(email, password, name, UserAccountDto.ROLE_ADMIN);
    audit.record(
        id,
        "ADMIN_BOOTSTRAP_CREATE",
        AuditTargets.USER,
        Long.toString(id),
        null,
        Map.of("email", email, "name", name, "role", UserAccountDto.ROLE_ADMIN));
    log.info("관리자 계정: {} 새로 만듦 (관리자가 없었음)", mask(email));
    return Optional.of(id);
  }

  /** 로그용: 이메일 앞 2글자만 보이게 가린다 (ab***@***) */
  static String mask(String email) {
    int at = email.indexOf('@');
    String local = at < 0 ? email : email.substring(0, at);
    String head = local.length() <= 2 ? local : local.substring(0, 2);
    return head + "***" + (at < 0 ? "" : "@***");
  }
}
