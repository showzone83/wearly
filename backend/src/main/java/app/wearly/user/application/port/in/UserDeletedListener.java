package app.wearly.user.application.port.in;

/**
 * 회원이 탈퇴(삭제)됐을 때 다른 컨텍스트가 할 일 (H1과 같은 방식: user가 정의하고 각 컨텍스트의 입력 어댑터가 구현한다). DB 밖에 둔 회원 데이터(예: auth의
 * 로그인 세션, Valkey)를 지우는 데 쓴다. DB의 하위 데이터는 FK ON DELETE CASCADE로 지워진다.
 */
public interface UserDeletedListener {

  /** 회원 행을 지운 뒤(같은 트랜잭션 안) 부른다 */
  void userDeleted(long userId);
}
