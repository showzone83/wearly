// 계절 테마 (이슈 #6). 테마(냥깨비·Closeta) 위에 계절마다 포인트 색·배경 틴트·보조 색 몇 개만 덮어쓴다.
// 전체 팔레트를 새로 만들지 않는다: 글자 색(text·textSub·textMuted), 카드 바탕(surface), 위험·그림자는 테마 값 그대로다.
// React Native·Expo·`@/` import가 없는 순수 TS라 관리자 사이트(미리보기)와 node 단위 테스트가 그대로 가져다 쓴다.
// themes.ts는 타입만 가져온다(실행 시 import 없음 — node가 확장자 없는 경로를 못 읽기 때문).
import type { ITheme, IThemeColors, TThemeId } from './themes';

export type TSeason = 'spring' | 'summer' | 'fall' | 'winter';

export const SEASONS: TSeason[] = ['spring', 'summer', 'fall', 'winter'];

/**
 * 서버 설정(GET /app/config의 seasonMode). OFF = 계절 색 없음(기본), AUTO = 날짜로 계절 고르기,
 * 나머지는 시즌 이벤트용 고정 계절.
 */
export type TSeasonMode = 'OFF' | 'AUTO' | 'SPRING' | 'SUMMER' | 'FALL' | 'WINTER';

export const SEASON_MODES: TSeasonMode[] = ['OFF', 'AUTO', 'SPRING', 'SUMMER', 'FALL', 'WINTER'];
export const DEFAULT_SEASON_MODE: TSeasonMode = 'OFF';

/** 사용자가 기기에서 고르는 값: 서버 설정 따르기(기본) · 끄기 */
export type TSeasonPref = 'follow' | 'off';
export const DEFAULT_SEASON_PREF: TSeasonPref = 'follow';

export const isSeasonMode = (v: unknown): v is TSeasonMode =>
  typeof v === 'string' && (SEASON_MODES as string[]).includes(v);

/** 서버 값 읽기. 없거나(옛 서버) 모르는 값(새 서버의 새 모드)이면 OFF — 앱이 그릴 수 없는 모드는 꾸미지 않는다 */
export const parseSeasonMode = (v: unknown): TSeasonMode => (isSeasonMode(v) ? v : DEFAULT_SEASON_MODE);

export const isSeasonPref = (v: unknown): v is TSeasonPref => v === 'follow' || v === 'off';

/** 고정 계절 모드 → 계절 */
const FIXED: Partial<Record<TSeasonMode, TSeason>> = {
  SPRING: 'spring',
  SUMMER: 'summer',
  FALL: 'fall',
  WINTER: 'winter',
};

/** 화면 문구·장식. 장식은 홈 인사 위 작은 알약 하나뿐이다(마스코트 그림은 바꾸지 않는다) */
export const SEASON_INFO: Record<TSeason, { label: string; icon: string; greeting: string }> = {
  spring: { label: '봄', icon: '🌸', greeting: '살랑살랑 봄이에요' },
  summer: { label: '여름', icon: '🌿', greeting: '싱그러운 여름이에요' },
  fall: { label: '가을', icon: '🍁', greeting: '선선한 가을이에요' },
  winter: { label: '겨울', icon: '❄️', greeting: '포근하게 입는 겨울이에요' },
};

export const SEASON_MODE_LABELS: Record<TSeasonMode, string> = {
  OFF: '끄기',
  AUTO: '자동 (날짜 따라)',
  SPRING: '봄 고정',
  SUMMER: '여름 고정',
  FALL: '가을 고정',
  WINTER: '겨울 고정',
};

// ── 계절 계산 ──

/** 한국 표준시(UTC+9). 서머타임이 없어 고정 오프셋으로 정확하다 */
const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;

/** 월(1~12) → 계절. 3~5월 봄, 6~8월 여름, 9~11월 가을, 12~2월 겨울 */
export const seasonOfMonth = (month: number): TSeason => {
  if (month >= 3 && month <= 5) return 'spring';
  if (month >= 6 && month <= 8) return 'summer';
  if (month >= 9 && month <= 11) return 'fall';
  return 'winter';
};

/** 그 순간의 한국 날짜의 월(1~12). 기기 시간대와 상관없다 — 해외 사용자도 한국과 같은 계절로 본다 */
export const seoulMonth = (now: Date): number => new Date(now.getTime() + SEOUL_OFFSET_MS).getUTCMonth() + 1;

/** 그 순간의 한국 날짜 'YYYY-MM-DD' (날짜가 바뀌었는지 볼 때) */
export const seoulDateKey = (now: Date): string => new Date(now.getTime() + SEOUL_OFFSET_MS).toISOString().slice(0, 10);

/** 그 순간의 계절 (한국 날짜 기준) */
export const seasonAt = (now: Date): TSeason => seasonOfMonth(seoulMonth(now));

/**
 * 지금 화면에 쓸 계절. 사용자가 껐거나 서버가 OFF면 null(테마 그대로).
 * AUTO는 날짜로 계산하고, 고정 모드는 날짜와 상관없이 그 계절이다.
 */
export const resolveSeason = (
  mode: TSeasonMode,
  pref: TSeasonPref = DEFAULT_SEASON_PREF,
  now: Date = new Date(),
): TSeason | null => {
  if (pref === 'off' || mode === 'OFF') return null;
  if (mode === 'AUTO') return seasonAt(now);
  return FIXED[mode] ?? null;
};

// ── 계절 색 (테마 토큰 덮어쓰기) ──

/** 계절이 바꾸는 토큰. 이 밖의 토큰은 테마 값을 그대로 쓴다 */
export type TSeasonToken =
  | 'bg'
  | 'surfaceAlt'
  | 'border'
  | 'chipBorder'
  | 'tabBar'
  | 'primary'
  | 'primaryPressed'
  | 'primarySoft'
  | 'accent'
  | 'accentSoft';

export type TSeasonColors = Pick<IThemeColors, TSeasonToken>;

/**
 * 테마별 계절 색. 냥깨비는 따뜻한 크림·짙은 초록 성격을 살려 채도를 낮게, Closeta는 밝은 아이보리·세이지 성격대로 한 톤 밝게.
 * 봄 = 벚꽃 핑크 + 연두, 여름 = 민트 + 하늘, 가을 = 테라코타·머스터드 + 올리브, 겨울 = 네이비 + 아이스 그레이.
 * 값을 바꾸면 tests/seasons.test.mjs의 대비 검사(WCAG AA 4.5:1)를 다시 돌린다.
 */
export const SEASON_COLORS: Record<TThemeId, Record<TSeason, TSeasonColors>> = {
  nyang: {
    spring: {
      bg: '#FCF2EF',
      surfaceAlt: '#F8E8E6',
      border: '#EEDDD8',
      chipBorder: '#EBDAD5',
      tabBar: '#FFFAF8',
      primary: '#356B45',
      primaryPressed: '#275236',
      primarySoft: '#E4F0DA',
      accent: '#EC8FA8',
      accentSoft: '#FBE2EA',
    },
    summer: {
      bg: '#F2F8F4',
      surfaceAlt: '#E4F2EC',
      border: '#D8E8E0',
      chipBorder: '#D5E6DE',
      tabBar: '#FAFDFB',
      primary: '#1F6457',
      primaryPressed: '#164C42',
      primarySoft: '#DCEFEA',
      accent: '#5DB3D9',
      accentSoft: '#DDF0F8',
    },
    fall: {
      bg: '#FAF0E3',
      surfaceAlt: '#F6E9DA',
      border: '#E9D6C0',
      chipBorder: '#E6D3BD',
      tabBar: '#FFF9F1',
      primary: '#4A5D2A',
      primaryPressed: '#384721',
      primarySoft: '#EDE7C9',
      accent: '#C96B3F',
      accentSoft: '#F6DECF',
    },
    winter: {
      bg: '#F3F4F5',
      surfaceAlt: '#E7EBEE',
      border: '#DCE1E6',
      chipBorder: '#D9DFE5',
      tabBar: '#FAFBFC',
      primary: '#2C4A66',
      primaryPressed: '#1F3650',
      primarySoft: '#E0E8F1',
      accent: '#8DB4DB',
      accentSoft: '#E3EDF7',
    },
  },
  closeta: {
    spring: {
      bg: '#FDF8F8',
      surfaceAlt: '#F8EDEF',
      border: '#EEE2E3',
      chipBorder: '#EDE0E2',
      tabBar: '#FFFFFF',
      primary: '#34724F',
      primaryPressed: '#285A3E',
      primarySoft: '#E5F2E0',
      accent: '#F09CB3',
      accentSoft: '#FDE9EF',
    },
    summer: {
      bg: '#F6FBFA',
      surfaceAlt: '#E6F5F3',
      border: '#DAEBE8',
      chipBorder: '#D7E9E6',
      tabBar: '#FFFFFF',
      primary: '#1C6E69',
      primaryPressed: '#155652',
      primarySoft: '#DDF1EF',
      accent: '#6CC0E6',
      accentSoft: '#E1F4FB',
    },
    fall: {
      bg: '#FCF8F2',
      surfaceAlt: '#F6EDE0',
      border: '#ECE1D2',
      chipBorder: '#E9DED0',
      tabBar: '#FFFFFF',
      primary: '#586A2E',
      primaryPressed: '#46552A',
      primarySoft: '#F1ECD4',
      accent: '#D98C3A',
      accentSoft: '#F9E9D2',
    },
    winter: {
      bg: '#F6F8FB',
      surfaceAlt: '#EAF0F6',
      border: '#DEE5ED',
      chipBorder: '#DBE3EC',
      tabBar: '#FFFFFF',
      primary: '#33557A',
      primaryPressed: '#264463',
      primarySoft: '#E2EAF3',
      accent: '#9BC1E3',
      accentSoft: '#E7F0F9',
    },
  },
};

/** 테마에 계절 색을 덮어쓴 새 테마 (순수 병합, 원본은 바꾸지 않는다). season이 null이면 원본 그대로 */
export const mergeSeason = (theme: ITheme, season: TSeason | null): ITheme => {
  if (!season) return theme;
  const over = SEASON_COLORS[theme.id]?.[season];
  if (!over) return theme;
  return { ...theme, colors: { ...theme.colors, ...over } };
};

// 화면 스타일(theme.ts makeStyles)은 테마 객체를 열쇠로 캐시하므로, 같은 테마·계절이면 같은 객체를 돌려준다
const cache = new WeakMap<ITheme, Partial<Record<TSeason, ITheme>>>();

/** mergeSeason과 같되 결과를 기억한다 (같은 입력 → 같은 객체) */
export const seasonalTheme = (theme: ITheme, season: TSeason | null): ITheme => {
  if (!season) return theme;
  let byTheme = cache.get(theme);
  if (!byTheme) {
    byTheme = {};
    cache.set(theme, byTheme);
  }
  return (byTheme[season] ??= mergeSeason(theme, season));
};

// ── 대비 (WCAG 2.x) ──

/** '#RRGGBB'의 상대 휘도 (0~1) */
export const relativeLuminance = (hex: string): number => {
  const n = parseInt(hex.replace('#', ''), 16);
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin((n >> 16) & 255) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
};

/** 두 색의 대비 (1~21). 순서는 상관없다 */
export const contrastRatio = (a: string, b: string): number => {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

/** 글자 색 · 바탕 색 조합 (앱에서 실제로 글자를 얹는 곳). textMuted(비활성·힌트)는 WCAG 예외라 넣지 않는다 */
export const TEXT_PAIRS: [keyof IThemeColors, keyof IThemeColors][] = [
  ['onPrimary', 'primary'],
  ['onPrimary', 'primaryPressed'],
  ['text', 'bg'],
  ['text', 'surface'],
  ['text', 'surfaceAlt'],
  ['text', 'primarySoft'],
  ['text', 'accentSoft'],
  ['text', 'tabBar'],
  ['textSub', 'bg'],
  ['textSub', 'surface'],
  ['textSub', 'surfaceAlt'],
  ['primary', 'bg'],
  ['primary', 'surface'],
  ['primary', 'surfaceAlt'],
  ['primary', 'primarySoft'],
  ['primary', 'tabBar'],
];

/** WCAG AA 본문 글자 기준 */
export const AA_TEXT = 4.5;

/** 기준에 못 미치는 조합 목록 (비어 있으면 통과) */
export const contrastIssues = (colors: IThemeColors, min = AA_TEXT) =>
  TEXT_PAIRS.map(([fg, bg]) => ({ fg, bg, ratio: contrastRatio(colors[fg], colors[bg]) })).filter((p) => p.ratio < min);
