// 예정일 코디의 장소 (doc/19): 최근 고른 장소(최대 5개)와 장소별 예보 기간을 기기에 저장한다.
// 장소 고르기 화면(app/place.tsx)이 고른 장소는 picked로 예정일 코디(PLAN 모드)에 넘긴다 (저장하지 않음).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { Place } from '@/data/types';
import { placeKey } from '@/lib/planWeather';

/** 최근 장소 개수 */
export const RECENT_PLACES_MAX = 5;

interface IPlaceState {
  /** 최근 고른 장소 (앞이 최신) */
  recent: Place[];
  /** 장소 키 → 예보 기간(오늘부터 며칠 뒤까지 예보가 있는지). 마지막 응답의 forecastUntil로 정한다 */
  spans: Record<string, number>;
  /** 장소 고르기 화면에서 막 고른 장소. nonce가 바뀌면 PLAN 모드가 받아 간다 */
  picked: { place: Place; nonce: number } | null;
  pick: (place: Place) => void;
  setSpan: (place: Place, span: number) => void;
}

export const usePlaceStore = create<IPlaceState>()(
  persist(
    (set, get) => ({
      recent: [],
      spans: {},
      picked: null,
      pick: (place) => {
        const key = placeKey(place);
        const recent = [place, ...get().recent.filter((p) => placeKey(p) !== key)].slice(0, RECENT_PLACES_MAX);
        set({ recent, picked: { place, nonce: (get().picked?.nonce ?? 0) + 1 } });
      },
      setSpan: (place, span) => {
        const key = placeKey(place);
        if (get().spans[key] === span) return;
        set({ spans: { ...get().spans, [key]: span } });
      },
    }),
    {
      name: 'wearly-place-v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ recent, spans }) => ({ recent, spans }),
    },
  ),
);
