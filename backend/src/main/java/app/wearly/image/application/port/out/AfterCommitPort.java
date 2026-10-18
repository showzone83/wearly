package app.wearly.image.application.port.out;

/** 진행 중인 트랜잭션이 커밋된 뒤 할 일 (저장소 객체 지우기). 트랜잭션 밖이면 바로 한다 */
public interface AfterCommitPort {

  void afterCommit(Runnable action);
}
