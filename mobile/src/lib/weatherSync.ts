// 서버 날씨 동기화 (doc/17 5장): 고른 지역의 날씨를 GET /weather로 받아 useWeatherStore에 넣는다.
// 서버는 저장된 값만 주므로 자주 불러도 외부 API 호출은 늘지 않는다. 실패(서버 없음·503 아직 받은 적 없음)해도
// 조용히 저장값 → 목업 날씨로 계속한다.
import { api } from '@/lib/api';
import { allCodes } from '@/lib/codes';
import { MOCK_MODE } from '@/lib/mode';
import { DEFAULT_REGION, useWeatherStore } from '@/store/useWeatherStore';

/**
 * 진행 중인 요청과 그 지역. 지역까지 같아야 요청을 함께 쓴다: "가까운 지역 찾기"는 위치 권한 창이 닫힐 때(AppState active)
 * 옛 지역 요청이 먼저 떠서, 지역을 따지지 않으면 새 지역 요청이 그 요청에 묻혀 나가지 않는다.
 */
let inflight: IInflight | null = null;

interface IInflight {
  region: string;
  promise: Promise<void>;
}

const fetchWeather = async (): Promise<void> => {
  const { regionCode, weather, etag } = useWeatherStore.getState();
  try {
    // 저장한 날씨가 다른 지역이면 ETag 없이 새로 받는다
    const sameRegion = weather?.region.code === regionCode;
    const res = await api.weather.current(regionCode, sameRegion ? etag : null);
    if (res.changed) useWeatherStore.getState().applyServerWeather(res.weather, res.etag);
  } catch (e) {
    if (__DEV__) console.log('[weather] 서버 날씨를 받지 못해 저장값·목업을 써요', (e as Error)?.message);
  }
};

/** GET /weather → 저장. 같은 지역은 동시에 여러 번 불러도 요청을 하나만 보내고, 다른 지역이면 앞 요청이 끝난 뒤 이어서 받는다 */
export const syncWeather = (): Promise<void> => {
  // 목업 실행은 서버 없이 기기 안 목업 날씨를 쓴다
  if (MOCK_MODE) return Promise.resolve();
  const region = useWeatherStore.getState().regionCode;
  if (inflight?.region === region) return inflight.promise;
  // fetchWeather는 실패해도 던지지 않으므로 앞 요청에 그대로 이어 붙인다 (응답이 섞이지 않게 한 번에 하나씩)
  const entry: IInflight = {
    region,
    promise: (inflight?.promise ?? Promise.resolve()).then(fetchWeather).finally(() => {
      if (inflight === entry) inflight = null;
    }),
  };
  inflight = entry;
  return entry.promise;
};

/** 지역을 바꾸고 바로 받는다 */
export const changeRegion = (code: string, picked = true): Promise<void> => {
  useWeatherStore.getState().setRegion(code, picked);
  return syncWeather();
};

export interface IRegion {
  code: string;
  label: string;
  lat: number;
  lon: number;
}

/** 고를 수 있는 지역 (코드 그룹 REGION의 활성 코드 중 위·경도가 있는 것) */
export function regionList(): IRegion[] {
  return allCodes('REGION')
    .filter((c) => c.active && typeof c.attrs.lat === 'number' && typeof c.attrs.lon === 'number')
    .map((c) => ({ code: c.code, label: c.label, lat: c.attrs.lat as number, lon: c.attrs.lon as number }));
}

/** 두 좌표 사이 거리(km, 하버사인) */
export function distanceKm(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(bLat - aLat);
  const dLon = rad(bLon - aLon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 가장 가까운 지역 (지역이 없으면 null). 위치는 기기 밖으로 보내지 않고 여기서만 계산한다 */
export function nearestRegion(lat: number, lon: number, regions: IRegion[] = regionList()): IRegion | null {
  let best: IRegion | null = null;
  let bestKm = Infinity;
  for (const r of regions) {
    const km = distanceKm(lat, lon, r.lat, r.lon);
    if (km < bestKm) {
      best = r;
      bestKm = km;
    }
  }
  return best;
}

/** 지역 이름 (코드에 없으면 코드 그대로) */
export function regionLabel(code: string): string {
  return allCodes('REGION').find((c) => c.code === code)?.label ?? (code === DEFAULT_REGION ? '서울' : code);
}
