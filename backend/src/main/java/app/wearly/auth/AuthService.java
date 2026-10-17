package app.wearly.auth;

import app.wearly.auth.AuthDtos.AuthResponse;
import app.wearly.auth.AuthDtos.LoginRequest;
import app.wearly.auth.AuthDtos.SignupRequest;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.User;
import app.wearly.user.UserDto;
import app.wearly.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** BCrypt가 받는 비밀번호 최대 길이 */
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final RefreshTokenService refreshTokens;
    private final JwtTokenService jwt;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** 없는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 */
    private final String dummyHash;

    public AuthService(UserRepository users, LocalCredentialRepository credentials, RefreshTokenService refreshTokens,
            JwtTokenService jwt, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("wearly-dummy-password-1");
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (req.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            // 64자 이내라도 한글 등은 한 글자가 3바이트라 BCrypt 한도를 넘을 수 있다 (넘기면 encode가 예외를 던져 500)
            throw ApiException.validation("password", "비밀번호가 너무 길어요. 한글이 섞이면 더 짧게 써 주세요.");
        }
        String email = normalizeEmail(req.email());
        if (credentials.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        try {
            User user = users.saveAndFlush(new User(email, req.name().strip()));
            credentials.saveAndFlush(new LocalCredential(user, email, passwordEncoder.encode(req.password())));
            return issueTokens(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입한 경우
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Optional<LocalCredential> found = credentials.findByEmail(normalizeEmail(req.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        LocalCredential credential = found.get();
        if (!passwordEncoder.matches(req.password(), credential.getPasswordHash()) || !credential.getUser().isActive()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        credential.markLogin(clock.instant());
        return issueTokens(credential.getUser());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.Issued next = refreshTokens.rotate(rawRefreshToken);
        User user = users.findById(next.entity().getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));
        return toResponse(user, next.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private AuthResponse issueTokens(User user) {
        RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.getId());
        return toResponse(user, refresh.rawToken());
    }

    private AuthResponse toResponse(User user, String rawRefreshToken) {
        return new AuthResponse(jwt.issue(user.getId()), jwt.accessTtlSeconds(), rawRefreshToken,
                refreshTokens.refreshTtlSeconds(), UserDto.from(user));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
