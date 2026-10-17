package app.wearly.admin.application.port.in;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장). 그 이메일 계정을 만들거나(이름 기본 "관리자") 이미 있으면 ADMIN으로 올린다.
 * 이미 있는 계정의 비밀번호·이름은 바꾸지 않는다.
 */
public interface EnsureAdminUseCase {

    /** @return 관리자 회원 ID */
    long ensureAdmin(String email, String password, String name);
}
