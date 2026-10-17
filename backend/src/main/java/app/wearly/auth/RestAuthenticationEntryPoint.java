package app.wearly.auth;

import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증 실패를 계약 4장 형식으로 응답한다.
 * - 토큰 없음 → UNAUTHORIZED
 * - 만료 → TOKEN_EXPIRED (앱이 이 코드로 자동 갱신한다)
 * - 그 밖의 토큰 오류(서명·형식·탈퇴 회원) → TOKEN_INVALID
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(response, classify(authException));
    }

    static ErrorCode classify(AuthenticationException e) {
        if (!(e instanceof OAuth2AuthenticationException)) {
            return ErrorCode.UNAUTHORIZED;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof JwtValidationException jve
                    && jve.getErrors().stream().anyMatch(err -> ExpiryValidator.EXPIRED_ERROR.equals(err.getErrorCode()))) {
                return ErrorCode.TOKEN_EXPIRED;
            }
        }
        return ErrorCode.TOKEN_INVALID;
    }
}
