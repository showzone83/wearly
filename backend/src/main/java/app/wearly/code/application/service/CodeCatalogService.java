package app.wearly.code.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.code.application.port.out.CodeSnapshotPort;
import app.wearly.sharedkernel.code.CodeSnapshot;
import org.springframework.stereotype.Service;

/** CodeCatalog 구현. 캐시는 출력 어댑터(CodeSnapshotPort)가 들고 있다 */
@Service
public class CodeCatalogService implements CodeCatalog {

  private final CodeSnapshotPort snapshots;

  public CodeCatalogService(CodeSnapshotPort snapshots) {
    this.snapshots = snapshots;
  }

  @Override
  public CodeSnapshot current() {
    return snapshots.current();
  }
}
