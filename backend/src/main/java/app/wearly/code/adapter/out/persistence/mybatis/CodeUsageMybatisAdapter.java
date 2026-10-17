package app.wearly.code.adapter.out.persistence.mybatis;

import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeUsageRowDto;
import app.wearly.code.adapter.out.persistence.mybatis.mapper.CodeMapper;
import app.wearly.code.application.port.out.CodeUsagePort;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** CodeUsagePort 구현 (MyBatis) */
@Component
class CodeUsageMybatisAdapter implements CodeUsagePort {

  private final CodeMapper mapper;

  CodeUsageMybatisAdapter(CodeMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Map<String, Long> itemUsage() {
    Map<String, Long> usage = new HashMap<>();
    for (CodeUsageRowDto r : mapper.countItemUsage()) {
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
