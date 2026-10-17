package app.wearly.user;

import app.wearly.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserDto me(long userId) {
        return users.findById(userId).map(UserDto::from).orElseThrow(ApiException::notFound);
    }

    /**
     * 탈퇴: 회원 행을 지우면 FK ON DELETE CASCADE로 자격 증명·Refresh 토큰·프로필·옷장·AI 기록이 모두 지워진다.
     * 남은 Access 토큰은 회원 조회에서 걸러져 401 TOKEN_INVALID가 된다.
     */
    @Transactional
    public void delete(long userId) {
        users.findById(userId).ifPresent(users::delete);
        users.flush();
    }
}
