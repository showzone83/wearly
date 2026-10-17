// 앱 디자인 테마 값과 마스코트 그림을 가져온다 (doc/12 2·4·6장).
// vite.config.ts의 별칭 '@app-themes' → mobile/src/data/themes.ts, '@app-mascots' → mobile/src/data/mascots/index.ts
import type { CSSProperties } from 'react';

import { MASCOT_POSES, mascotArt } from '@app-mascots';
import { DEFAULT_THEME_ID, isThemeId, THEME_IDS, THEMES } from '@app-themes';
import type { IMascotArt, IMascotShape, TMascotId, TMascotPose } from '@app-mascots';
import type { ITheme, IThemeColors, TThemeId } from '@app-themes';

export { DEFAULT_THEME_ID, isThemeId, MASCOT_POSES, mascotArt, THEME_IDS, THEMES };
export type { IMascotArt, IMascotShape, ITheme, IThemeColors, TMascotId, TMascotPose, TThemeId };

/** 자세 이름 → 화면에 보일 이름 (doc/12 4장 표) */
export const POSE_LABELS: Record<TMascotPose, string> = {
  splash: '첫 화면',
  hello: '인사',
  style: '취향 고르기',
  camera: '옷 등록',
  closet: '옷장',
  cheer: '응원',
};

/** 색 견본에 보일 토큰과 설명. 앞쪽이 화면에서 많이 보이는 색이다 */
export const COLOR_TOKENS: { key: keyof IThemeColors; label: string }[] = [
  { key: 'primary', label: '주 색' },
  { key: 'primaryPressed', label: '주 색 눌림' },
  { key: 'primarySoft', label: '주 색 연한 바탕' },
  { key: 'accent', label: '포인트' },
  { key: 'accentSoft', label: '포인트 연한 바탕' },
  { key: 'bg', label: '화면 바탕' },
  { key: 'surface', label: '카드 바탕' },
  { key: 'surfaceAlt', label: '연한 강조 바탕' },
  { key: 'border', label: '테두리' },
  { key: 'text', label: '제목·본문' },
  { key: 'textSub', label: '보조 글자' },
  { key: 'textMuted', label: '흐린 글자' },
  { key: 'onPrimary', label: '주 색 위 글자' },
  { key: 'chip', label: '칩 바탕' },
  { key: 'chipBorder', label: '칩 테두리' },
  { key: 'tabBar', label: '하단 탭 바' },
  { key: 'danger', label: '위험' },
  { key: 'shadow', label: '그림자' },
];

/** 테마 이름. 모르는 id면 id 그대로 */
export const themeName = (id: string | null | undefined): string => {
  if (!id) return '—';
  return isThemeId(id) ? THEMES[id].name : id;
};

/** 미리보기 폰은 실제 화면의 약 0.6배라 모서리도 같이 줄인다 */
const SCALE = 0.6;

/** 테마 색·모서리 → 미리보기 CSS 변수 (components/ThemePreview, styles.css의 .tp-*) */
export const themeVars = (theme: ITheme): CSSProperties => {
  const vars: Record<string, string> = {};
  for (const [k, v] of Object.entries(theme.colors)) vars[`--t-${k}`] = v;
  vars['--t-r-sm'] = `${Math.round(theme.radius.sm * SCALE)}px`;
  vars['--t-r-md'] = `${Math.round(theme.radius.md * SCALE)}px`;
  vars['--t-r-lg'] = `${Math.round(theme.radius.lg * SCALE)}px`;
  return vars as CSSProperties;
};
