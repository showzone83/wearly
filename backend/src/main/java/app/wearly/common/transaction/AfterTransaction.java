package app.wearly.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 트랜잭션이 끝난 뒤 실행 (Spring 트랜잭션 동기화). 트랜잭션 밖에서 부르면 바로 실행한다. 출력 어댑터가 같이 쓴다 (L10) */
public final class AfterTransaction {

  private AfterTransaction() {}

  /** 커밋된 경우에만 */
  public static void commit(Runnable action) {
    register(action, true);
  }

  /** 커밋·롤백 모두 */
  public static void completion(Runnable action) {
    register(action, false);
  }

  private static void register(Runnable action, boolean commitOnly) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            if (commitOnly) {
              action.run();
            }
          }

          @Override
          public void afterCompletion(int status) {
            if (!commitOnly) {
              action.run();
            }
          }
        });
  }
}
