package app.wearly.ai.adapter.out.transaction;

import app.wearly.ai.application.port.out.TransactionHooksPort;
import app.wearly.common.transaction.AfterTransaction;
import org.springframework.stereotype.Component;

/** TransactionHooksPort 구현 (Spring 트랜잭션 동기화) */
@Component
class TransactionHooksAdapter implements TransactionHooksPort {

  @Override
  public void afterCompletion(Runnable action) {
    AfterTransaction.completion(action);
  }
}
