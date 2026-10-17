// 현재 앱 테마 (doc/12 3장). 서버 설정(GET /app/config)의 themeId를 따르고, 마지막 값을 기기에 저장해
// 다음 실행 때 바로 그린다. 서버에 닿지 못하면 저장된 값, 그것도 없으면 기본 테마.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { DEFAULT_THEME_ID, isThemeId, THEMES, type ITheme, type TThemeId } from '@/data/themes';

interface IThemeState {
  themeId: TThemeId;
  theme: ITheme;
  setThemeId: (id: string | null | undefined) => void;
}

export const useThemeStore = create<IThemeState>()(
  persist(
    (set) => ({
      themeId: DEFAULT_THEME_ID,
      theme: THEMES[DEFAULT_THEME_ID],
      setThemeId: (id) => {
        const next = isThemeId(id) ? id : DEFAULT_THEME_ID;
        set({ themeId: next, theme: THEMES[next] });
      },
    }),
    {
      name: 'wearly-theme-v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ themeId }) => ({ themeId }),
      onRehydrateStorage: () => (state) => state?.setThemeId(state.themeId),
    },
  ),
);

/** 화면에서 쓰는 테마 값 */
export const useTheme = () => useThemeStore((s) => s.theme);
