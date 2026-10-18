// 서버 날씨 (doc/17 5장). 서버가 주기적으로 받아 둔 값을 GET /weather로 읽어 기기에 저장한다.
// 다음 실행 때 바로 쓰고, ETag가 같으면 304로 그대로 둔다. 서버에 닿지 못하면 저장값, 그것도 없으면 목업 날씨(lib/weather.ts).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { WeatherDto } from '@/api/types';

/** 지역을 고르지 않았을 때 (서버 기본값과 같다) */
export const DEFAULT_REGION = 'SEOUL';

interface IWeatherState {
  /** 사용자가 고른 지역 (코드 그룹 REGION) */
  regionCode: string;
  /** true면 사용자가 직접 골랐다 (위치로 자동 선택하지 않는다) */
  regionPicked: boolean;
  /** 마지막으로 받은 서버 날씨. regionCode와 다른 지역일 수 있다 (지역을 바꾼 직후) */
  weather: WeatherDto | null;
  /** 다음 요청의 If-None-Match (weather와 짝) */
  etag: string | null;
  setRegion: (code: string, picked: boolean) => void;
  applyServerWeather: (weather: WeatherDto, etag: string | null) => void;
}

export const useWeatherStore = create<IWeatherState>()(
  persist(
    (set, get) => ({
      regionCode: DEFAULT_REGION,
      regionPicked: false,
      weather: null,
      etag: null,
      setRegion: (code, picked) => {
        if (code === get().regionCode) {
          if (picked) set({ regionPicked: true });
          return;
        }
        // 다른 지역의 ETag는 쓰지 않는다
        set({ regionCode: code, regionPicked: picked || get().regionPicked, etag: null });
      },
      applyServerWeather: (weather, etag) => set({ weather, etag }),
    }),
    {
      name: 'wearly-weather-v1',
      storage: createJSONStorage(() => AsyncStorage),
    },
  ),
);

/** 지금 고른 지역의 서버 날씨 (지역을 막 바꿔 아직 받지 못했으면 null) */
export function currentServerWeather(): WeatherDto | null {
  const { weather, regionCode } = useWeatherStore.getState();
  return weather && weather.region.code === regionCode ? weather : null;
}
