// 현재 앱 테마 (doc/12 3장, 계절 테마 이슈 #6). 서버 설정(GET /app/config)의 themeId·seasonMode를 따르고, 마지막 값을
// 기기에 저장해 다음 실행 때 바로 그린다. 서버에 닿지 못하면 저장된 값, 그것도 없으면 기본 테마(계절 없음).
// 화면에 쓰는 theme = 테마 토큰 + 계절 덮어쓰기(data/seasons.ts). 사용자는 마이 탭에서 계절 테마를 끌 수 있다(기기 저장).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import {
  DEFAULT_SEASON_MODE,
  DEFAULT_SEASON_PREF,
  isSeasonMode,
  isSeasonPref,
  parseSeasonMode,
  resolveSeason,
  seasonalTheme,
  type TSeason,
  type TSeasonMode,
  type TSeasonPref,
} from '@/data/seasons';
import { DEFAULT_THEME_ID, isThemeId, THEMES, type ITheme, type TThemeId } from '@/data/themes';

interface IThemeState {
  themeId: TThemeId;
  /** 서버가 정한 계절 모드 */
  seasonMode: TSeasonMode;
  /** 사용자가 이 기기에서 고른 값: 서버 설정 따르기 · 끄기 */
  seasonPref: TSeasonPref;
  /** 지금 적용 중인 계절 (없으면 계절 색 없음) */
  season: TSeason | null;
  /** 화면에서 쓰는 테마 (계절 색을 덮어쓴 값) */
  theme: ITheme;
  /** 마지막으로 받은 서버 설정의 ETag (다음 요청의 If-None-Match) */
  configEtag: string | null;
  setThemeId: (id: string | null | undefined) => void;
  /** 계절 모드 바꾸기 (목업 실행의 개발용 버튼) */
  setSeasonMode: (mode: string | null | undefined) => void;
  setSeasonPref: (pref: TSeasonPref) => void;
  /** 서버 설정 반영. 앱이 모르는 테마 ID면 아무것도 바꾸지 않는다(ETag도 저장하지 않아 다음에 다시 받는다) */
  applyServerConfig: (config: { themeId: string; seasonMode?: string }, etag: string) => void;
  /** 날짜로 계절을 다시 계산한다 (AUTO일 때 앱이 다시 활성이 되면). 계절이 같으면 아무것도 하지 않는다 */
  refreshSeason: (now?: Date) => void;
}

/** 테마 ID·모드·사용자 선택·날짜 → 지금 계절과 화면 테마 */
const compose = (themeId: TThemeId, seasonMode: TSeasonMode, seasonPref: TSeasonPref, now = new Date()) => {
  const season = resolveSeason(seasonMode, seasonPref, now);
  return { season, theme: seasonalTheme(THEMES[themeId], season) };
};

export const useThemeStore = create<IThemeState>()(
  persist(
    (set, get) => ({
      themeId: DEFAULT_THEME_ID,
      seasonMode: DEFAULT_SEASON_MODE,
      seasonPref: DEFAULT_SEASON_PREF,
      season: null,
      theme: THEMES[DEFAULT_THEME_ID],
      configEtag: null,
      setThemeId: (id) => {
        const next = isThemeId(id) ? id : DEFAULT_THEME_ID;
        const { seasonMode, seasonPref } = get();
        set({ themeId: next, ...compose(next, seasonMode, seasonPref) });
      },
      setSeasonMode: (mode) => {
        const next = parseSeasonMode(mode);
        const { themeId, seasonPref } = get();
        set({ seasonMode: next, ...compose(themeId, next, seasonPref) });
      },
      setSeasonPref: (pref) => {
        const next = isSeasonPref(pref) ? pref : DEFAULT_SEASON_PREF;
        const { themeId, seasonMode } = get();
        set({ seasonPref: next, ...compose(themeId, seasonMode, next) });
      },
      applyServerConfig: (config, etag) => {
        if (!isThemeId(config.themeId)) {
          console.warn('[theme] 모르는 테마 ID라 무시해요', config.themeId);
          return;
        }
        const seasonMode = parseSeasonMode(config.seasonMode);
        set({
          themeId: config.themeId,
          seasonMode,
          configEtag: etag,
          ...compose(config.themeId, seasonMode, get().seasonPref),
        });
      },
      refreshSeason: (now) => {
        const { themeId, seasonMode, seasonPref, season } = get();
        const next = compose(themeId, seasonMode, seasonPref, now);
        if (next.season !== season) set(next);
      },
    }),
    {
      name: 'wearly-theme-v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ themeId, seasonMode, seasonPref, configEtag }) => ({ themeId, seasonMode, seasonPref, configEtag }),
      // 저장값을 확인하고(옛 저장값엔 계절 값이 없다) 오늘 날짜로 계절·테마를 다시 만든다
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        const seasonMode = isSeasonMode(state.seasonMode) ? state.seasonMode : DEFAULT_SEASON_MODE;
        const seasonPref = isSeasonPref(state.seasonPref) ? state.seasonPref : DEFAULT_SEASON_PREF;
        const themeId = isThemeId(state.themeId) ? state.themeId : DEFAULT_THEME_ID;
        useThemeStore.setState({ themeId, seasonMode, seasonPref, ...compose(themeId, seasonMode, seasonPref) });
      },
    },
  ),
);

/** 화면에서 쓰는 테마 값 */
export const useTheme = () => useThemeStore((s) => s.theme);
