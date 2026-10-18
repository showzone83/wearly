package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.WeatherCallFilterDto;
import app.wearly.weather.application.port.in.dto.PatchWeatherProviderDto;
import app.wearly.weather.application.port.in.dto.ProviderTestResultDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDetailDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDto;
import app.wearly.weather.application.port.in.dto.UpdateWeatherSettingDto;
import app.wearly.weather.application.port.in.dto.WeatherCallDto;
import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherScheduleDto;
import app.wearly.weather.application.port.in.dto.WeatherSnapshotDto;
import java.util.List;

/** 관리자 날씨 관리 (17 문서 6장). 실제 일은 weather 컨텍스트가 하고, 여기서는 목록 페이지·검색 조건 검증·변경 이력을 맡는다 */
public interface AdminWeatherUseCase {

  WeatherScheduleDto schedule();

  /** 바뀐 뒤 스케줄 화면 */
  WeatherScheduleDto updateSetting(long adminId, UpdateWeatherSettingDto in);

  List<WeatherProviderDto> providers();

  WeatherProviderDto patchProvider(long adminId, String code, PatchWeatherProviderDto patch);

  WeatherProviderDto resetProvider(long adminId, String code);

  ProviderTestResultDto testProvider(long adminId, String code, String regionCode);

  /**
   * @return 실행 ID
   */
  long refresh(long adminId, String regionCode);

  PageDto<RefreshRunDto> runs(String trigger, String status, PagingDto paging);

  RefreshRunDetailDto run(long runId);

  PageDto<WeatherCallDto> calls(WeatherCallFilterDto filter, PagingDto paging);

  WeatherCallSummaryDto callSummary();

  List<WeatherSnapshotDto> snapshots();

  WeatherSnapshotDto snapshot(String regionCode);
}
