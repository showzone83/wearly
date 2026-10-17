package app.wearly.code.application.port.out;

import app.wearly.sharedkernel.code.CodeSnapshot;

/** 코드 스냅샷 읽기 (MyBatis + 메모리 캐시) */
public interface CodeSnapshotPort {

  CodeSnapshot current();

  /** 진행 중인 트랜잭션이 커밋되면 다시 읽는다 (트랜잭션 밖이면 바로) */
  void refreshAfterCommit();
}
