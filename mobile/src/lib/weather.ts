import type { Weather } from '@/data/types';
import { diffDays, fromKey, todayKey } from './date';

// 목업 날씨: 실제 API 대신 서울 월평균 기온에 날짜별 고정 난수를 더한다.
// 같은 날짜는 항상 같은 값을 돌려주므로 화면 간 결과가 일관된다.
const SEOUL_MONTHLY_AVG = [-2, 1, 6, 13, 18, 23, 26, 26, 22, 15, 8, 1];

/** 예보를 제공하는 기간(일). 이후 날짜는 직접 입력 */
export const FORECAST_DAYS = 10;

function seeded(key: string, salt: number): number {
  let h = 2166136261 ^ salt;
  for (let i = 0; i < key.length; i++) {
    h ^= key.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 10000) / 10000;
}

export function forecast(key: string): Weather {
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

export function hasForecast(key: string): boolean {
  const n = diffDays(key, todayKey());
  return n >= 0 && n < FORECAST_DAYS;
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
