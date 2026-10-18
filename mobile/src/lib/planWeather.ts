// 예정일 코디의 장소별 날씨 규칙 (doc/19). 화면·스토어·서버 호출 없이 순수 함수만 둔다 (단위 점검 대상).
// - 응답(GET /weather/day) → 추천 엔진의 Weather 변환
// - 다시 확인할 예정 고르기 (평년값으로 저장했고, 이제 예보 기간 안에 들어온 것, 하루 한 번)
// - 예보가 저장값과 달라졌는지 (기온 3도 · 비 여부 rainProb 50 기준)
// - 예전 서버(이 API가 없음) 오류 판단
import type { WeatherDayResponse, WeatherDayWhere } from '@/api/types';
import type { PlannedEvent, Place, TWeatherSource, Weather } from '@/data/types';

/** 이만큼(°C) 이상 달라지면 예보가 나왔다고 알린다 */
export const RECHECK_TEMP_DIFF = 3;
/** 비 여부 기준 (강수 확률 %) */
export const RAIN_PROB_LINE = 50;
/** 장소의 예보 기간을 아직 모를 때: 오늘 + 2일 (서버 기본 예보 3일) */
export const DEFAULT_FORECAST_SPAN = 2;

export const SOURCE_LABEL: Record<TWeatherSource, string> = {
  FORECAST: '예보',
  CLIMATE: '평년',
  MANUAL: '직접 입력',
};

/** 장소 비교·캐시 키. 지역 코드가 있으면 코드, 없으면 좌표(소수 둘째 자리) */
export const placeKey = (p: Place): string => (p.regionCode ? p.regionCode : `${p.lat.toFixed(2)},${p.lon.toFixed(2)}`);

export const samePlace = (a: Place | null | undefined, b: Place | null | undefined): boolean =>
  !!a && !!b && placeKey(a) === placeKey(b);

/** GET /weather/day 요청 장소. 지역 코드가 있으면 코드(인증 불필요), 없으면 좌표(로그인 필요) */
export const dayWhere = (p: Place): WeatherDayWhere =>
  p.regionCode ? { region: p.regionCode } : { lat: Math.round(p.lat * 1e4) / 1e4, lon: Math.round(p.lon * 1e4) / 1e4 };

/** 좌표로 불러야 하는 장소인지 (로그인 필요) */
export const needsLogin = (p: Place): boolean => !p.regionCode;

/** 응답 → 추천 엔진 Weather. 기온은 앱 규칙대로 정수로 반올림. 평년값은 날씨 상태가 없어 강수 비율로 정한다 */
export const weatherFromDay = (res: WeatherDayResponse): Weather => {
  const rainProb = Math.max(0, Math.min(100, Math.round(res.day.rainProb)));
  const temp = Math.round(res.day.temp);
  return {
    temp,
    min: Math.min(Math.round(res.day.min), temp),
    max: Math.max(Math.round(res.day.max), temp),
    rainProb,
    cond: res.day.cond ?? (rainProb >= 60 ? 'RAIN' : 'CLOUDY'),
  };
};

export const isRainy = (w: Pick<Weather, 'rainProb'>): boolean => w.rainProb >= RAIN_PROB_LINE;

/** 예보가 코디를 바꿀 만큼 달라졌는지: 기온 3도 이상 또는 비 여부가 바뀜 */
export const weatherChanged = (prev: Weather, next: Weather): boolean =>
  Math.abs(prev.temp - next.temp) >= RECHECK_TEMP_DIFF || isRainy(prev) !== isRainy(next);

/** 저장된 예정의 날씨 출처. 예전 데이터(weatherSource 없음)는 manual 플래그로 정한다 */
export const planSource = (p: Pick<PlannedEvent, 'weatherSource' | 'weather'>): TWeatherSource =>
  p.weatherSource ?? (p.weather.manual ? 'MANUAL' : 'FORECAST');

/** 로컬 날짜 YYYY-MM-DD */
const keyOf = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const parseKey = (key: string): Date => {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
};

export const addDaysKey = (key: string, n: number): string => {
  const d = parseKey(key);
  d.setDate(d.getDate() + n);
  return keyOf(d);
};

const daysBetween = (a: string, b: string): number => Math.round((parseKey(a).getTime() - parseKey(b).getTime()) / 86400000);

/** ISO 시각의 로컬 날짜 (읽지 못하면 null) */
export const localDateOf = (iso: string | undefined): string | null => {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : keyOf(d);
};

/** 응답의 예보 기간: 오늘부터 며칠 뒤까지 예보가 있는지 (forecastUntil이 없으면 null) */
export const forecastSpan = (res: Pick<WeatherDayResponse, 'forecastUntil'>, today: string): number | null =>
  res.forecastUntil ? Math.max(0, daysBetween(res.forecastUntil, today)) : null;

export interface IRecheckOptions {
  /** 오늘 (로컬 YYYY-MM-DD) */
  today: string;
  /** 장소(없으면 내 지역)의 예보 기간(오늘부터 며칠 뒤까지). 모르면 DEFAULT_FORECAST_SPAN */
  spanOf: (place: Place | undefined) => number;
  /** 좌표 장소를 부를 수 있는지 (로그인) */
  canUseCoords: boolean;
}

/**
 * 다시 확인할 예정: 평년값(CLIMATE)으로 저장했고, 날짜가 지나지 않았고, 이제 그 장소 예보 기간 안에 들어왔고,
 * 오늘 아직 확인하지 않았고, 이미 "예보가 나왔어요"가 아닌 것.
 */
export const pickRecheckTargets = (plans: PlannedEvent[], opts: IRecheckOptions): PlannedEvent[] =>
  plans.filter((p) => {
    if (planSource(p) !== 'CLIMATE' || p.forecastReady) return false;
    if (p.date < opts.today) return false;
    if (p.place && needsLogin(p.place) && !opts.canUseCoords) return false;
    if (localDateOf(p.weatherCheckedAt) === opts.today) return false;
    return p.date <= addDaysKey(opts.today, opts.spanOf(p.place));
  });

export type TRecheckResult =
  /** 아직 평년값: 확인 시각만 */
  | { kind: 'still-climate'; plan: PlannedEvent }
  /** 예보가 나왔고 코디를 바꿀 만큼 달라짐 → 배지 */
  | { kind: 'ready'; plan: PlannedEvent }
  /** 예보가 나왔지만 차이가 작거나 아직 코디가 없음 → 조용히 예보로 */
  | { kind: 'quiet'; plan: PlannedEvent };

/** 다시 확인한 응답을 예정에 반영한다 */
export const applyRecheck = (plan: PlannedEvent, res: WeatherDayResponse, nowIso: string): TRecheckResult => {
  if (res.source !== 'FORECAST') return { kind: 'still-climate', plan: { ...plan, weatherCheckedAt: nowIso } };
  const next = weatherFromDay(res);
  const ready = !!plan.outfit && weatherChanged(plan.weather, next);
  return {
    kind: ready ? 'ready' : 'quiet',
    plan: { ...plan, weather: next, weatherSource: 'FORECAST', weatherCheckedAt: nowIso, forecastReady: ready },
  };
};

/**
 * 예전 서버(GET /weather/day가 없음)의 응답인지. 로그인 요청은 404 NOT_FOUND,
 * 지역 코드 요청(토큰 없음)은 공개 경로가 아니라서 401 UNAUTHORIZED가 온다. 새 서버는 지역 코드 요청에 401을 주지 않는다
 */
export const isUnsupported = (e: { status: number; code: string }, where: WeatherDayWhere | 'search'): boolean =>
  e.status === 404 || (where !== 'search' && 'region' in where && e.status === 401 && e.code === 'UNAUTHORIZED');

/** 평년값 안내: "서울의 10월 평년 기온(최저 12° · 최고 20°) 기준이에요. 예보가 나오면 다시 확인할게요." */
export const climateNotice = (label: string, date: string, w: Pick<Weather, 'min' | 'max'>): string =>
  `${label}의 ${parseKey(date).getMonth() + 1}월 평년 기온(최저 ${w.min}° · 최고 ${w.max}°) 기준이에요. 예보가 나오면 다시 확인할게요.`;
