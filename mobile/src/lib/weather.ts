import type { Weather } from '@/data/types';
import { currentServerWeather } from '@/store/useWeatherStore';
import { diffDays, fromKey, todayKey, toKey } from './date';

// 날씨: 서버가 받아 둔 예보(GET /weather, doc/17)가 있으면 그 값을 쓰고,
// 없으면(목업 실행·서버에 닿지 못함·아직 받은 적 없음) 목업 날씨를 쓴다.
// 목업은 서울 월평균 기온에 날짜별 고정 난수를 더한다. 같은 날짜는 항상 같은 값을 돌려주므로 화면 간 결과가 일관된다.
const SEOUL_MONTHLY_AVG = [-2, 1, 6, 13, 18, 23, 26, 26, 22, 15, 8, 1];

/** 서버 예보가 없을 때 목업 예보를 주는 기간(일). 이후 날짜는 직접 입력 */
export const FORECAST_DAYS = 10;

function seeded(key: string, salt: number): number {
  let h = 2166136261 ^ salt;
  for (let i = 0; i < key.length; i++) {
    h ^= key.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 10000) / 10000;
}

function mockForecast(key: string): Weather {
  const d = fromKey(key);
  const avg = SEOUL_MONTHLY_AVG[d.getMonth()];
  const temp = Math.round(avg + (seeded(key, 1) - 0.5) * 8);
  const range = 6 + Math.round(seeded(key, 2) * 6);
  const rainRoll = seeded(key, 3);
  const rainProb = rainRoll > 0.8 ? 60 + Math.round(rainRoll * 30) : Math.round(rainRoll * 40);
  const cond: Weather['cond'] = rainProb >= 60 ? 'RAIN' : seeded(key, 4) > 0.5 ? 'CLOUDY' : 'SUNNY';
  return {
    temp,
    min: temp - Math.ceil(range / 2),
    max: temp + Math.floor(range / 2),
    rainProb,
    cond,
  };
}

/** 서버 예보에서 그 날짜 (없으면 null). 기온은 앱 규칙대로 정수로 반올림한다 */
function serverForecast(key: string): Weather | null {
  const day = currentServerWeather()?.days.find((d) => d.date === key);
  if (!day) return null;
  return {
    temp: Math.round(day.temp),
    min: Math.round(day.min),
    max: Math.round(day.max),
    rainProb: day.rainProb,
    cond: day.cond,
  };
}

export function forecast(key: string): Weather {
  return serverForecast(key) ?? mockForecast(key);
}

/**
 * 예보가 있는 날짜인지. 서버 예보를 받았으면 서버가 준 날짜만(공급자가 주는 기간, 보통 3일),
 * 아니면 목업 기간(오늘부터 FORECAST_DAYS일). 그 밖의 날짜는 체감 온도를 직접 고른다.
 */
export function hasForecast(key: string): boolean {
  const server = currentServerWeather();
  if (server) return server.days.some((d) => d.date === key);
  const n = diffDays(key, todayKey());
  return n >= 0 && n < FORECAST_DAYS;
}

/**
 * 홈 화면의 "지금" 날씨. 서버 값이 없거나 오늘 받은 값이 아니면 null (오늘 예보만 보여 준다).
 * stale은 받은 시점의 서버 판정이라, 기기가 오래 오프라인이면 지난 값을 "지금"으로 보이지 않게 날짜로 한 번 더 거른다.
 */
export function currentWeather(): { temp: number; condText: string | null; stale: boolean; region: string } | null {
  const server = currentServerWeather();
  if (!server || toKey(new Date(server.fetchedAt)) !== todayKey()) return null;
  return {
    temp: Math.round(server.current.temp),
    condText: server.current.condText,
    stale: server.stale,
    region: server.region.label,
  };
}

export const COND_LABEL: Record<Weather['cond'], string> = {
  SUNNY: '맑음',
  CLOUDY: '구름조금',
  RAIN: '비',
};

/** 사용자가 고르는 체감 온도 → 기온 값 (예보 기간 밖 예정일용) */
export const FEEL_PRESETS = [
  { label: '추움', temp: 0 },
  { label: '쌀쌀', temp: 8 },
  { label: '선선', temp: 15 },
  { label: '따뜻', temp: 21 },
  { label: '더움', temp: 28 },
];

export function manualWeather(temp: number, rain: boolean): Weather {
  return {
    temp,
    min: temp - 4,
    max: temp + 4,
    rainProb: rain ? 70 : 10,
    cond: rain ? 'RAIN' : 'SUNNY',
    manual: true,
  };
}

/** 날씨 안내 카드 문구 (비, 큰 일교차) */
export function weatherNotice(w: Weather): string | null {
  if (w.rainProb >= 60) return '비 예보가 있어요. 우산을 챙기고, 젖어도 부담 없는 소재를 골랐어요.';
  if (w.max - w.min >= 10) return `일교차가 ${w.max - w.min}°C로 커요. 벗기 쉬운 아우터를 더했어요.`;
  return null;
}
