package app.wearly.ai.application.port.out;

/** 진행 중인 트랜잭션이 끝난 뒤(커밋·롤백 모두) 할 일. 트랜잭션 밖이면 바로 한다 */
public interface TransactionHooksPort {

  void afterCompletion(Runnable action);
}
