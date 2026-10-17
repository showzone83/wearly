package app.wearly.code.adapter.out.query;

import app.wearly.code.application.port.out.CodeUsagePort;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** CodeUsagePort 구현 (MyBatis) */
@Component
class CodeUsageQueryAdapter implements CodeUsagePort {

  private final CodeQueryMapper mapper;

  CodeUsageQueryAdapter(CodeQueryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Map<String, Long> itemUsage() {
    Map<String, Long> usage = new HashMap<>();
    for (CodeUsageRow r : mapper.countItemUsage()) {
      usage.merge(r.getGroupCode() + ":" + r.getCode(), r.getCount(), Long::sum);
    }
    return usage;
  }

  @Override
  public long countItemsUsing(String group, String code) {
    return mapper.countItemsUsing(group, code);
  }

  @Override
  public long countProfilesUsing(String group, String code) {
    return mapper.countProfilesUsing(group, code);
  }
}
