package app.wearly.weather.adapter.out.persistence.mybatis;

import app.wearly.weather.adapter.out.persistence.mybatis.mapper.WeatherLogMapper;
import app.wearly.weather.application.port.out.WeatherLogQueryPort;
import app.wearly.weather.application.port.out.dto.CallRowDto;
import app.wearly.weather.application.port.out.dto.CallSummaryRowDto;
import app.wearly.weather.application.port.out.dto.HistoryRowDto;
import app.wearly.weather.application.port.out.dto.ProviderUsageRowDto;
import app.wearly.weather.application.port.out.dto.RunRowDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** WeatherLogQueryPort 구현 (MyBatis) */
@Component
class WeatherLogMybatisAdapter implements WeatherLogQueryPort {

  private final WeatherLogMapper mapper;

  WeatherLogMybatisAdapter(WeatherLogMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<RunRowDto> findRuns(RunFilter filter, int limit, long offset) {
    return mapper.findRuns(filter, limit, offset);
  }

  @Override
  public long countRuns(RunFilter filter) {
    return mapper.countRuns(filter);
  }

  @Override
  public List<CallRowDto> findCalls(CallFilter filter, int limit, long offset) {
    return mapper.findCalls(filter, limit, offset);
  }

  @Override
  public long countCalls(CallFilter filter) {
    return mapper.countCalls(filter);
  }

  @Override
  public List<ProviderUsageRowDto> usage(Instant from, Instant to) {
    return mapper.usage(from, to);
  }

  @Override
  public CallSummaryRowDto summary(Instant from, Instant to) {
    return mapper.summary(from, to);
  }

  @Override
  public List<HistoryRowDto> findHistory(HistoryFilter filter, int limit, long offset) {
    return mapper.findHistory(filter, limit, offset);
  }

  @Override
  public long countHistory(HistoryFilter filter) {
    return mapper.countHistory(filter);
  }
}
