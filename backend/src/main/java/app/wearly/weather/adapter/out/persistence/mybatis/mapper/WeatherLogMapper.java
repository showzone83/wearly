package app.wearly.weather.adapter.out.persistence.mybatis.mapper;

import app.wearly.weather.application.port.out.WeatherLogQueryPort.CallFilter;
import app.wearly.weather.application.port.out.WeatherLogQueryPort.HistoryFilter;
import app.wearly.weather.application.port.out.WeatherLogQueryPort.RunFilter;
import app.wearly.weather.application.port.out.dto.CallRowDto;
import app.wearly.weather.application.port.out.dto.CallSummaryRowDto;
import app.wearly.weather.application.port.out.dto.HistoryRowDto;
import app.wearly.weather.application.port.out.dto.ProviderUsageRowDto;
import app.wearly.weather.application.port.out.dto.RunRowDto;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 날씨 실행·호출 이력과 날씨 이력 조회 (resources/mapper/weather/WeatherLogMapper.xml) */
@Mapper
public interface WeatherLogMapper {

  List<RunRowDto> findRuns(
      @Param("f") RunFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countRuns(@Param("f") RunFilter filter);

  List<CallRowDto> findCalls(
      @Param("f") CallFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countCalls(@Param("f") CallFilter filter);

  List<ProviderUsageRowDto> usage(@Param("from") Instant from, @Param("to") Instant to);

  CallSummaryRowDto summary(@Param("from") Instant from, @Param("to") Instant to);

  List<HistoryRowDto> findHistory(
      @Param("f") HistoryFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countHistory(@Param("f") HistoryFilter filter);
}
