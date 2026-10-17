package app.wearly.admin;

import app.wearly.auth.LocalCredential;
import app.wearly.auth.LocalCredentialRepository;
import app.wearly.config.WearlyProperties;
import app.wearly.user.User;
import app.wearly.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작할 때 WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD가 있으면 그 계정을 만들거나 기존 계정을 ADMIN으로 올린다.
 * - 이름은 WEARLY_ADMIN_NAME (기본 "관리자"). 기존 계정의 이름·비밀번호는 바꾸지 않는다.
 * - 정지된 기존 계정이면 다시 ACTIVE로 돌린다 (관리자가 잠겨 들어가지 못하는 일을 막는다).
 * - 비밀번호는 가입과 같은 규칙(8~64자, 영문+숫자). 어기면 만들지 않고 WARN만 남긴다.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final WearlyProperties properties;
    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminBootstrap(WearlyProperties properties, UserRepository users, LocalCredentialRepository credentials,
            PasswordEncoder passwordEncoder, AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        WearlyProperties.Admin admin = properties.admin();
        if (admin == null || !admin.configured()) {
            return;
        }
        String email = admin.email().strip().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches() || email.length() > 255) {
            log.warn("WEARLY_ADMIN_EMAIL 형식이 맞지 않아 관리자 계정을 만들지 않았습니다.");
            return;
        }
        var existing = credentials.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get().getUser();
            if (user.isAdmin() && user.isActive()) {
                log.info("관리자 계정 확인: {}", email);
                return;
            }
            Map<String, Object> before = state(user);
            user.changeRole(User.ROLE_ADMIN);
            user.changeStatus(User.ACTIVE);
            users.saveAndFlush(user);
            audit.record(null, "ADMIN_BOOTSTRAP_PROMOTE", AuditService.USER, String.valueOf(user.getId()), before,
                    state(user));
            log.info("기존 계정을 관리자로 올렸습니다: {}", email);
            return;
        }
        if (!PASSWORD.matcher(admin.password()).matches()) {
            log.warn("WEARLY_ADMIN_PASSWORD는 8~64자, 영문과 숫자를 모두 포함해야 합니다. 관리자 계정을 만들지 않았습니다.");
            return;
        }
        String name = admin.name() == null || admin.name().isBlank() ? "관리자" : admin.name().strip();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        User user = new User(email, name);
        user.changeRole(User.ROLE_ADMIN);
        users.saveAndFlush(user);
        credentials.saveAndFlush(new LocalCredential(user, email, passwordEncoder.encode(admin.password())));
        audit.record(null, "ADMIN_BOOTSTRAP_CREATE", AuditService.USER, String.valueOf(user.getId()), null, state(user));
        log.info("관리자 계정을 만들었습니다: {}", email);
    }

    static Map<String, Object> state(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", user.getEmail());
        m.put("name", user.getName());
        m.put("role", user.getRole());
        m.put("status", user.getStatus());
        return m;
    }
}
