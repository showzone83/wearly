// 현재 앱 테마 (doc/12 3장). 서버 설정(GET /app/config)의 themeId를 따르고, 마지막 값을 기기에 저장해
// 다음 실행 때 바로 그린다. 서버에 닿지 못하면 저장된 값, 그것도 없으면 기본 테마.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { DEFAULT_THEME_ID, isThemeId, THEMES, type ITheme, type TThemeId } from '@/data/themes';

interface IThemeState {
  themeId: TThemeId;
  theme: ITheme;
  /** 마지막으로 받은 서버 설정의 ETag (다음 요청의 If-None-Match) */
  configEtag: string | null;
  setThemeId: (id: string | null | undefined) => void;
  /** 서버 설정 반영. 앱이 모르는 테마 ID면 지금 테마를 그대로 둔다 */
  applyServerTheme: (id: string, etag: string) => void;
}

export const useThemeStore = create<IThemeState>()(
  persist(
    (set) => ({
      themeId: DEFAULT_THEME_ID,
      theme: THEMES[DEFAULT_THEME_ID],
      configEtag: null,
      setThemeId: (id) => {
        const next = isThemeId(id) ? id : DEFAULT_THEME_ID;
        set({ themeId: next, theme: THEMES[next] });
      },
      applyServerTheme: (id, etag) => {
        if (!isThemeId(id)) {
          console.warn('[theme] 모르는 테마 ID라 무시해요', id);
          return;
        }
        set({ themeId: id, theme: THEMES[id], configEtag: etag });
      },
    }),
    {
      name: 'wearly-theme-v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ themeId, configEtag }) => ({ themeId, configEtag }),
      onRehydrateStorage: () => (state) => state?.setThemeId(state.themeId),
    },
  ),
);

/** 화면에서 쓰는 테마 값 */
export const useTheme = () => useThemeStore((s) => s.theme);
