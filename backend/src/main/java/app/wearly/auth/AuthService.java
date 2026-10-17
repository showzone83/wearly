package app.wearly.auth;

import app.wearly.auth.AuthDtos.AuthResponse;
import app.wearly.auth.AuthDtos.LoginRequest;
import app.wearly.auth.AuthDtos.SignupRequest;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.User;
import app.wearly.user.UserDto;
import app.wearly.user.UserRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

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
        if (!passwordEncoder.matches(req.password(), credential.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 비밀번호가 맞을 때만 정지 여부를 알린다 (07 문서 2장)
        if (!credential.getUser().isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
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
        return new AuthResponse(jwt.issue(user.getId(), user.getRole()), jwt.accessTtlSeconds(), rawRefreshToken,
                refreshTokens.refreshTtlSeconds(), UserDto.from(user));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
