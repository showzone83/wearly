package app.wearly.user.application.port.in;

public interface ProfileUseCase {

    /** 저장된 프로필이 없으면 기본값 (이름은 회원 이름) */
    ProfileData get(long userId);

    /** PUT: 프로필 전체를 바꾸고 회원 이름도 함께 바꾼다. 색·취향은 코드 테이블로 검증한다 */
    ProfileData put(long userId, ProfileData data);
}
