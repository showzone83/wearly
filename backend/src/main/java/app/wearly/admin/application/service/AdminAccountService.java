package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.EnsureAdminUseCase;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.user.application.port.in.UserAccount;
import app.wearly.user.application.port.in.UserUseCase;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장). 계정 생성은 auth, 역할 변경은 user 컨텍스트의 유스케이스로 하고
 * 만들기·승격을 변경 이력(ADMIN_BOOTSTRAP_CREATE·ADMIN_BOOTSTRAP_PROMOTE)에 남긴다.
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
    public long ensureAdmin(String rawEmail, String password, String rawName) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        String name = rawName == null || rawName.isBlank() ? DEFAULT_NAME : rawName.strip();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        Optional<Long> found = accounts.findUserIdByEmail(email);
        if (found.isPresent()) {
            UserAccount user = users.get(found.get());
            if (!user.isAdmin()) {
                String before = user.role();
                users.changeAccess(user.id(), user.status(), UserAccount.ROLE_ADMIN);
                audit.record(user.id(), "ADMIN_BOOTSTRAP_PROMOTE", AuditTargets.USER, Long.toString(user.id()),
                        Map.of("role", before), Map.of("role", UserAccount.ROLE_ADMIN));
                log.info("관리자 계정: 기존 회원 {}을(를) ADMIN으로 올림", email);
            } else {
                log.info("관리자 계정: {} (이미 ADMIN)", email);
            }
            return user.id();
        }
        long id = accounts.createAccount(email, password, name, UserAccount.ROLE_ADMIN);
        audit.record(id, "ADMIN_BOOTSTRAP_CREATE", AuditTargets.USER, Long.toString(id), null,
                Map.of("email", email, "name", name, "role", UserAccount.ROLE_ADMIN));
        log.info("관리자 계정: {} 새로 만듦", email);
        return id;
    }
}
