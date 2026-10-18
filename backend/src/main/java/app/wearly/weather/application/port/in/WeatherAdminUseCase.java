package app.wearly.weather.application.port.in;

import app.wearly.weather.application.port.in.dto.CallSearchDto;
import app.wearly.weather.application.port.in.dto.HistorySearchDto;
import app.wearly.weather.application.port.in.dto.PageResultDto;
import app.wearly.weather.application.port.in.dto.PatchWeatherProviderDto;
import app.wearly.weather.application.port.in.dto.ProviderTestResultDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDetailDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDto;
import app.wearly.weather.application.port.in.dto.RunSearchDto;
import app.wearly.weather.application.port.in.dto.UpdateWeatherSettingDto;
import app.wearly.weather.application.port.in.dto.WeatherCallDto;
import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto;
import app.wearly.weather.application.port.in.dto.WeatherHistoryDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherScheduleDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherSnapshotDto;
import java.util.List;

/**
 * 관리자 날씨 관리 (17 문서 6장). admin 컨텍스트가 부르고 변경 이력은 admin이 같은 트랜잭션에 남긴다. 실행·시험 호출은 외부 HTTP라 트랜잭션 없이 돈다.
 */
public interface WeatherAdminUseCase {

  WeatherScheduleDto schedule();

  WeatherSettingChangeDto updateSetting(long adminId, UpdateWeatherSettingDto in);

  List<WeatherProviderDto> providers();

  WeatherProviderChangeDto patchProvider(
      long adminId, String providerCode, PatchWeatherProviderDto patch);

  /** 차단 해제: 연속 실패와 차단을 비운다 */
  WeatherProviderChangeDto resetProvider(long adminId, String providerCode);

  /** 공급자 하나로 한 지역을 불러 본다. 저장된 날씨·공급자 상태는 그대로다 */
  ProviderTestResultDto testProvider(long adminId, String providerCode, String regionCode);

  /**
   * 지금 실행 (요청 스레드 밖에서 돈다).
   *
   * @param regionCode null이면 활성 지역 전체
   * @return 실행 ID (상태는 runs/{runId}로 확인). 실행 중이면 409 WEATHER_REFRESH_RUNNING, 공급자가 없으면 409
   *     WEATHER_NO_PROVIDER
   */
  long requestRefresh(long adminId, String regionCode);

  PageResultDto<RefreshRunDto> runs(RunSearchDto search, int limit, long offset);

  RefreshRunDetailDto run(long runId);

  PageResultDto<WeatherCallDto> calls(CallSearchDto search, int limit, long offset);

  WeatherCallSummaryDto callSummary();

  /** 모든 REGION 코드(비활성 포함)와 그 저장값 */
  List<WeatherSnapshotDto> snapshots();

  /** 원본 응답까지 */
  WeatherSnapshotDto snapshot(String regionCode);

  /** 지역·날짜별 날씨 이력 (최신 날짜순). 기간이 없으면 오늘까지 최근 30일 */
  PageResultDto<WeatherHistoryDto> history(HistorySearchDto search, int limit, long offset);
}
