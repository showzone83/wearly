package app.wearly.user.application.port.in;

/**
 * 회원이 탈퇴를 신청했을 때(탈퇴 예정, doc/27) 다른 컨텍스트가 바로 할 일. {@link UserDeletedListener}와 같은 방식: user가 정의하고 각
 * 컨텍스트의 입력 어댑터가 구현한다. 탈퇴 신청 트랜잭션 안에서 불린다. 회원 데이터(옷·사진)는 취소할 수 있도록 지우지 않는다 — 지우는 일은 유예 기간 뒤 {@link
 * UserDeletedListener}가 한다.
 */
public interface UserWithdrawnListener {

  void userWithdrawn(long userId);
}
