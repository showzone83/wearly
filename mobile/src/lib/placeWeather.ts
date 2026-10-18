// 예정일 코디의 장소별 날씨 (doc/19): GET /weather/day 호출·짧은 캐시, 예정 날씨 다시 확인.
// 규칙(변환·대상 고르기·차이 판단)은 lib/planWeather.ts의 순수 함수에 있다.
// 목업 실행이나 이 API가 없는 예전 서버에서는 아무것도 하지 않고, 화면은 지금처럼 체감 온도를 직접 고른다.
import { useEffect, useState } from 'react';

import { isApiError } from '@/api/errors';
import type { WeatherDayResponse } from '@/api/types';
import type { Place } from '@/data/types';
import { usePlaceStore } from '@/store/usePlaceStore';
import { useStore } from '@/store/useStore';
import { useWeatherStore } from '@/store/useWeatherStore';
import { api, errorMessage } from './api';
import { todayKey } from './date';
import { systemTraceId } from './errorText';
import { MOCK_MODE } from './mode';
import { pushPlan } from './planSync';
import {
  applyRecheck,
  DEFAULT_FORECAST_SPAN,
  dayWhere,
  forecastSpan,
  isUnsupported,
  needsLogin,
  pickRecheckTargets,
  placeKey,
} from './planWeather';
import { regionLabel, regionList } from './weatherSync';

export type TDayOutcome =
  | { kind: 'ok'; res: WeatherDayResponse }
  /** 목업 실행 · 예전 서버(API 없음) → 지금처럼 */
  | { kind: 'unsupported' }
  /** 좌표 장소인데 로그인하지 않음 */
  | { kind: 'login' }
  /** 503 WEATHER_UNAVAILABLE · 429 WEATHER_LIMIT · 네트워크 등 */
  | { kind: 'error'; code: string; message: string; traceId?: string };

/** 같은 장소·날짜 응답을 다시 쓰는 시간 */
const CACHE_MS = 10 * 60_000;
/** 예전 서버라고 판단하면 이 시간 동안 다시 부르지 않는다 (서버가 새로 뜨면 그 뒤에 쓴다) */
const UNSUPPORTED_MS = 10 * 60_000;

const cache = new Map<string, { at: number; res: WeatherDayResponse }>();
const inflight = new Map<string, Promise<TDayOutcome>>();
let unsupportedAt = 0;

const isAccount = (): boolean => useStore.getState().authMode === 'account';

/** 좌표 장소(검색한 곳)를 쓸 수 있는지: 서버 실행 + 로그인 */
export const canUseCoords = (): boolean => !MOCK_MODE && isAccount();

/** 장소별 날씨를 쓰는 실행인지 (목업 실행은 지금처럼 서버 없이) */
export const placeWeatherEnabled = (): boolean => !MOCK_MODE;

/** 내 지역(날씨 지역 설정)을 장소로. 좌표는 코드 attrs (없으면 0) */
export const myPlace = (regionCode: string = useWeatherStore.getState().regionCode): Place => {
  const r = regionList().find((x) => x.code === regionCode);
  return { regionCode, label: r?.label ?? regionLabel(regionCode), lat: r?.lat ?? 0, lon: r?.lon ?? 0 };
};

/** 예전 서버라고 판단해 잠시 부르지 않는 중인지 */
const dayApiMissing = (): boolean => Date.now() - unsupportedAt < UNSUPPORTED_MS;

/** 캐시에 있는 그 장소·날짜의 응답 (없거나 오래됐으면 null) */
const peekDay = (place: Place, date: string): WeatherDayResponse | null => {
  const hit = cache.get(`${placeKey(place)}|${date}`);
  return hit && Date.now() - hit.at < CACHE_MS ? hit.res : null;
};

/** 그 장소·날짜의 날씨. 실패해도 throw하지 않고 결과 종류로 돌려준다 */
export const fetchDayWeather = (place: Place, date: string, fresh = false): Promise<TDayOutcome> => {
  if (MOCK_MODE || dayApiMissing()) return Promise.resolve({ kind: 'unsupported' });
  if (needsLogin(place) && !canUseCoords()) return Promise.resolve({ kind: 'login' });
  const key = `${placeKey(place)}|${date}`;
  const hit = fresh ? null : peekDay(place, date);
  if (hit) return Promise.resolve({ kind: 'ok', res: hit });
  const running = inflight.get(key);
  if (running) return running;

  const where = dayWhere(place);
  const job = (async (): Promise<TDayOutcome> => {
    try {
      const res = await api.weather.day(where, date);
      cache.set(key, { at: Date.now(), res });
      const span = forecastSpan(res, todayKey());
      if (span !== null) usePlaceStore.getState().setSpan(place, span);
      return { kind: 'ok', res };
    } catch (e) {
      if (isApiError(e) && isUnsupported(e, where)) {
        unsupportedAt = Date.now();
        if (__DEV__) console.log('[place-weather] 서버에 /weather/day가 없어 지금처럼 동작해요');
        return { kind: 'unsupported' };
      }
      return {
        kind: 'error',
        code: isApiError(e) ? e.code : 'UNKNOWN',
        message: errorMessage(e, '날씨를 받지 못했어요.'),
        traceId: systemTraceId(e),
      };
    }
  })().finally(() => inflight.delete(key));
  inflight.set(key, job);
  return job;
};

/** PLAN 모드: 고른 장소·날짜의 날씨. 날짜를 빠르게 넘길 때 요청이 몰리지 않게 조금 기다렸다 부른다 */
export const usePlanDayWeather = (place: Place, date: string, enabled: boolean) => {
  const [state, setState] = useState<{ key: string; outcome: TDayOutcome } | null>(null);

  const key = `${placeKey(place)}|${date}`;

  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    const timer = setTimeout(() => {
      void fetchDayWeather(place, date).then((outcome) => {
        if (alive) setState({ key, outcome });
      });
    }, 250);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
    // place 객체는 매번 새로 만들어지므로 키로 비교한다
  }, [key, enabled]);

  // 캐시에 있거나 예전 서버로 판단했으면 기다리지 않고 바로 보여 준다
  const cached = enabled ? peekDay(place, date) : null;
  const outcome: TDayOutcome | null = !enabled
    ? null
    : cached
      ? { kind: 'ok', res: cached }
      : dayApiMissing()
        ? { kind: 'unsupported' }
        : state?.key === key
          ? state.outcome
          : null;
  return { loading: enabled && !outcome, outcome };
};

let recheckRunning: Promise<void> | null = null;

/**
 * 예정 날씨 다시 확인 (앱 시작·활성화·캘린더 진입). 평년값으로 저장한 예정이 이제 예보 기간 안에 들어왔으면
 * 예보를 받아, 기온 3도 이상·비 여부가 바뀌면 forecastReady(배지), 아니면 조용히 예보로 바꾼다. 같은 예정은 하루 한 번.
 */
export const recheckPlans = (): Promise<void> => {
  if (MOCK_MODE) return Promise.resolve();
  if (!recheckRunning) {
    recheckRunning = (async () => {
      const today = todayKey();
      const home = myPlace();
      const spans = usePlaceStore.getState().spans;
      const targets = pickRecheckTargets(useStore.getState().plans, {
        today,
        spanOf: (p) => spans[placeKey(p ?? home)] ?? DEFAULT_FORECAST_SPAN,
        canUseCoords: canUseCoords(),
      });
      for (const plan of targets) {
        const out = await fetchDayWeather(plan.place ?? home, plan.date, true);
        if (out.kind === 'unsupported') return; // 예전 서버: 아무것도 하지 않는다
        if (out.kind === 'error') {
          // 한도를 넘었으면 오늘은 그만 부른다. 그 밖의 실패(네트워크·503)는 다음 기회에 다시
          if (out.code === 'WEATHER_LIMIT') {
            useStore.getState().updatePlanWeather(plan.id, {
              weather: plan.weather,
              weatherSource: plan.weatherSource,
              weatherCheckedAt: new Date().toISOString(),
              forecastReady: plan.forecastReady,
            });
            void pushPlan(plan.id); // 계정 모드: 서버 예정에도 (게스트·목업은 아무것도 안 한다)
          }
          continue;
        }
        if (out.kind !== 'ok') continue;
        const { plan: next, kind } = applyRecheck(plan, out.res, new Date().toISOString());
        useStore.getState().updatePlanWeather(plan.id, {
          weather: next.weather,
          weatherSource: next.weatherSource,
          weatherCheckedAt: next.weatherCheckedAt,
          forecastReady: next.forecastReady,
        });
        void pushPlan(plan.id);
        if (__DEV__) console.log('[place-weather] 예정 다시 확인', plan.date, kind);
      }
    })()
      .catch((e) => {
        if (__DEV__) console.log('[place-weather] 다시 확인 실패', (e as Error)?.message);
      })
      .finally(() => {
        recheckRunning = null;
      });
  }
  return recheckRunning;
};
