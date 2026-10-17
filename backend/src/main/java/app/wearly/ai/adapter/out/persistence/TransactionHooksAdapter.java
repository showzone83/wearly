package app.wearly.ai.adapter.out.persistence;

import app.wearly.ai.application.port.out.TransactionHooksPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** TransactionHooksPort 구현 (Spring 트랜잭션 동기화) */
@Component
class TransactionHooksAdapter implements TransactionHooksPort {

  @Override
  public void afterCompletion(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
