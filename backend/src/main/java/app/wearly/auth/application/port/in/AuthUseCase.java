package app.wearly.auth.application.port.in;

/** 가입·로그인·토큰 갱신·로그아웃 (05 문서 6장) */
public interface AuthUseCase {

    AuthResult signup(SignupCommand command);

    AuthResult login(LoginCommand command);

    /** 토큰이 없거나 비어 있어도 401 REFRESH_INVALID */
    AuthResult refresh(String rawRefreshToken);

    /** 토큰이 없거나 틀려도 조용히 끝낸다 */
    void logout(String rawRefreshToken);
}
