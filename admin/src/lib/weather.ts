// 날씨 관리 화면(pages/weather/)의 순수 함수. 화면 없이 단위 테스트한다 (tests/weather.test.mjs).
// 값을 import하지 않는다 — node --test가 이 파일을 바로 읽는다(타입 import는 지워진다).
import type {
  TWeatherCond,
  TWeatherKeyState,
  TWeatherProviderStatus,
  TWeatherRunStatus,
  TWeatherTrigger,
} from '@/api';

const MINUTES_PER_DAY = 1440;
const DAYS_PER_MONTH = 30;

/**
 * 예상 월 호출 수 = 활성 지역 수 × 지역당 호출 수 × 하루 실행 수 × 30.
 * 지금 실행·시험 호출은 넣지 않는다 (관리자가 누를 때마다 더 쓴다).
 */
export const estimateMonthlyCalls = (regions: number, callsPerRegion: number, intervalMinutes: number): number => {
  if (!(intervalMinutes > 0) || regions <= 0 || callsPerRegion <= 0) return 0;
  return Math.round(regions * callsPerRegion * (MINUTES_PER_DAY / intervalMinutes) * DAYS_PER_MONTH);
};

export type TUsageLevel = 'ok' | 'warn' | 'over' | 'none';

/** 월 사용량 막대 색: 80% 이상 주황, 100% 이상 빨강, 한도가 없으면 none */
export const usageLevel = (used: number, quota: number | null | undefined): TUsageLevel => {
  if (quota === null || quota === undefined) return 'none';
  if (quota <= 0 || used >= quota) return 'over';
  return used / quota >= 0.8 ? 'warn' : 'ok';
};

/** 막대 너비 % (0~100). 한도가 없으면 0 */
export const usagePercent = (used: number, quota: number | null | undefined): number => {
  if (!quota || quota <= 0) return quota === 0 ? 100 : 0;
  return Math.min(100, Math.round((used / quota) * 100));
};

export const STATUS_TEXT: Record<TWeatherProviderStatus, string> = {
  OK: '정상',
  OPEN: '차단 중',
  HALF_OPEN: '다음 실행에서 시험',
  KEY_INVALID: '키 확인 필요',
};

export const STATUS_TONE: Record<TWeatherProviderStatus, 'green' | 'red' | 'beige'> = {
  OK: 'green',
  OPEN: 'red',
  HALF_OPEN: 'beige',
  KEY_INVALID: 'red',
};

export const KEY_TEXT: Record<TWeatherKeyState, string> = {
  CONFIGURED: '키 있음',
  MISSING: '키 없음',
  MOCK: '목업 실행',
  UNSUPPORTED: '서버에 구현 없음',
};

export const TRIGGER_TEXT: Record<TWeatherTrigger, string> = {
  SCHEDULE: '주기',
  MANUAL: '지금 실행',
  STARTUP: '서버 시작',
  TEST: '시험 호출',
};

export const RUN_STATUS_TEXT: Record<TWeatherRunStatus, string> = {
  RUNNING: '실행 중',
  SUCCESS: '성공',
  PARTIAL: '일부 실패',
  FAILED: '실패',
  SKIPPED: '건너뜀',
};

export const RUN_STATUS_TONE: Record<TWeatherRunStatus, 'green' | 'red' | 'beige' | 'gray' | 'ink'> = {
  RUNNING: 'ink',
  SUCCESS: 'green',
  PARTIAL: 'beige',
  FAILED: 'red',
  SKIPPED: 'gray',
};

export const SKIP_TEXT: Record<string, string> = {
  DISABLED: '스케줄 꺼짐',
  NO_PROVIDER: '쓸 수 있는 공급자 없음',
  LOCKED: '다른 실행 중',
};

export const COND_TEXT: Record<TWeatherCond, string> = {
  SUNNY: '맑음',
  CLOUDY: '흐림',
  RAIN: '비·눈',
};

export const COND_ICON: Record<TWeatherCond, string> = {
  SUNNY: '☀',
  CLOUDY: '☁',
  RAIN: '☂',
};

export const ERROR_TEXT: Record<string, string> = {
  TIMEOUT: '응답 시간 초과',
  CLIENT: '연결 실패',
  AUTH: '인증 실패 (키)',
  QUOTA: '공급자 월 한도 초과',
  HTTP_429: '호출 너무 많음 (429)',
  HTTP_4XX: '요청 오류 (4xx)',
  HTTP_5XX: '공급자 서버 오류 (5xx)',
  PARSE: '응답을 읽지 못함',
};

/** 공급자 상태 한 줄 설명 (배지 옆). until은 차단이 풀리는 시각 문구 */
export const statusDetail = (
  status: TWeatherProviderStatus,
  keyState: TWeatherKeyState,
  until: string | null,
): string | null => {
  if (keyState === 'MISSING') return '서버 설정에 키가 없어 이 공급자는 건너뜁니다.';
  if (keyState === 'UNSUPPORTED') return '서버에 이 공급자 구현이 없습니다.';
  if (status === 'KEY_INVALID') return '키가 틀렸거나 막혔습니다. 키를 고친 뒤 "차단 해제"를 누르세요.';
  if (status === 'OPEN') return until ? `연속 실패로 ${until}까지 건너뜁니다.` : '연속 실패로 잠시 건너뜁니다.';
  if (status === 'HALF_OPEN') return '차단 시간이 끝나 다음 실행의 첫 지역으로 다시 시험합니다.';
  return null;
};

/** 기온 표시 (소수 첫째 자리까지, 정수면 정수) */
export const fmtTemp = (t: number | null | undefined): string => {
  if (t === null || t === undefined || Number.isNaN(t)) return '—';
  return `${Number.isInteger(t) ? t : t.toFixed(1)}°`;
};

/** 실행 소요 시간(ms). 끝나지 않았으면 null */
export const runDurationMs = (startedAt: string, finishedAt: string | null): number | null => {
  if (!finishedAt) return null;
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime();
  return Number.isNaN(ms) ? null : Math.max(0, ms);
};
