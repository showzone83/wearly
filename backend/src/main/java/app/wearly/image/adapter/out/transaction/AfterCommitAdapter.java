package app.wearly.image.adapter.out.transaction;

import app.wearly.common.transaction.AfterTransaction;
import app.wearly.image.application.port.out.AfterCommitPort;
import org.springframework.stereotype.Component;

/** AfterCommitPort 구현 (Spring 트랜잭션 동기화) */
@Component
class AfterCommitAdapter implements AfterCommitPort {

  @Override
  public void afterCommit(Runnable action) {
    AfterTransaction.commit(action);
  }
}
