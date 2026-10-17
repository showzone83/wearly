package app.wearly.admin;

import app.wearly.auth.LocalCredential;
import app.wearly.auth.LocalCredentialRepository;
import app.wearly.config.WearlyProperties;
import app.wearly.user.User;
import app.wearly.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장). WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD가 있으면
 * 그 이메일 계정을 만들거나(이름 WEARLY_ADMIN_NAME, 기본 "관리자") 이미 있으면 ADMIN으로 올린다.
 * 이미 있는 계정의 비밀번호·이름은 바꾸지 않는다.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    static final String DEFAULT_NAME = "관리자";

    private final WearlyProperties.Admin props;
    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public AdminBootstrap(WearlyProperties properties, UserRepository users, LocalCredentialRepository credentials,
            PasswordEncoder passwordEncoder, AuditService audit, TransactionTemplate tx) {
        this.props = properties.admin();
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.configured()) {
            log.info("관리자 계정 설정 없음 (WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD). 관리자 사이트에 들어가려면 둘 다 넣고 다시 시작하세요.");
            return;
        }
        ensureAdmin(props.email(), props.password(), props.name());
    }

    /** @return 관리자 회원 ID */
    public long ensureAdmin(String rawEmail, String password, String rawName) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        String name = rawName == null || rawName.isBlank() ? DEFAULT_NAME : rawName.strip();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        String finalName = name;
        Long id = tx.execute(status -> {
            Optional<LocalCredential> found = credentials.findByEmail(email);
            if (found.isPresent()) {
                User user = found.get().getUser();
                if (!user.isAdmin()) {
                    String before = user.getRole();
                    user.changeRole(User.ROLE_ADMIN);
                    users.saveAndFlush(user);
                    audit.record(user.getId(), "ADMIN_BOOTSTRAP_PROMOTE", AuditService.USER, user.getId().toString(),
                            Map.of("role", before), Map.of("role", User.ROLE_ADMIN));
                    log.info("관리자 계정: 기존 회원 {}을(를) ADMIN으로 올림", email);
                } else {
                    log.info("관리자 계정: {} (이미 ADMIN)", email);
                }
                return user.getId();
            }
            User user = new User(email, finalName);
            user.changeRole(User.ROLE_ADMIN);
            users.saveAndFlush(user);
            credentials.saveAndFlush(new LocalCredential(user, email, passwordEncoder.encode(password)));
            audit.record(user.getId(), "ADMIN_BOOTSTRAP_CREATE", AuditService.USER, user.getId().toString(), null,
                    Map.of("email", email, "name", finalName, "role", User.ROLE_ADMIN));
            log.info("관리자 계정: {} 새로 만듦", email);
            return user.getId();
        });
        return id;
    }
}
