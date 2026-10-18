// 앱 설정 > 테마의 계절 모드 화면 도우미 (이슈 #6). 계절 색과 계절 계산은 앱 코드(mobile/src/data/seasons.ts, 별칭 '@app-seasons')에 있고,
// 여기는 관리자 화면 문구만 둔다. import가 없는 순수 함수라 node 단위 테스트(tests/seasonMode.test.mjs)가 그대로 읽는다.

export type TSeasonModeKey = 'OFF' | 'AUTO' | 'SPRING' | 'SUMMER' | 'FALL' | 'WINTER';
export type TSeasonKey = 'spring' | 'summer' | 'fall' | 'winter';

/** 서버가 받는 계절 모드 (화면 순서) */
export const SEASON_MODE_KEYS: TSeasonModeKey[] = ['OFF', 'AUTO', 'SPRING', 'SUMMER', 'FALL', 'WINTER'];

export const isSeasonModeKey = (v: unknown): v is TSeasonModeKey =>
  typeof v === 'string' && (SEASON_MODE_KEYS as string[]).includes(v);

export const SEASON_LABEL: Record<TSeasonKey, string> = { spring: '봄', summer: '여름', fall: '가을', winter: '겨울' };

/** AUTO가 고르는 달 (한국 날짜 기준) */
export const SEASON_PERIOD: Record<TSeasonKey, string> = {
  spring: '3~5월',
  summer: '6~8월',
  fall: '9~11월',
  winter: '12~2월',
};

/** 고정 모드 → 계절 */
export const FIXED_SEASON: Partial<Record<TSeasonModeKey, TSeasonKey>> = {
  SPRING: 'spring',
  SUMMER: 'summer',
  FALL: 'fall',
  WINTER: 'winter',
};

export const SEASON_MODE_TEXT: Record<TSeasonModeKey, { label: string; desc: string }> = {
  OFF: { label: '끄기', desc: '계절 색 없이 테마 기본 색으로 보여요 (지금 화면 그대로).' },
  AUTO: {
    label: '자동',
    desc: '앱이 한국 날짜로 계절을 골라요: 3~5월 봄, 6~8월 여름, 9~11월 가을, 12~2월 겨울. 해외에 있는 사용자도 같은 계절이에요.',
  },
  SPRING: { label: '봄 고정', desc: '시즌 이벤트용: 날짜와 상관없이 봄 색으로 보여요.' },
  SUMMER: { label: '여름 고정', desc: '시즌 이벤트용: 날짜와 상관없이 여름 색으로 보여요.' },
  FALL: { label: '가을 고정', desc: '시즌 이벤트용: 날짜와 상관없이 가을 색으로 보여요.' },
  WINTER: { label: '겨울 고정', desc: '시즌 이벤트용: 날짜와 상관없이 겨울 색으로 보여요.' },
};

/** 계절 모드와 오늘 계절(AUTO일 때 앱이 고를 계절) → 지금 앱에 보이는 계절. OFF·모르는 값이면 null */
export const appliedSeason = (mode: string | null | undefined, today: TSeasonKey): TSeasonKey | null => {
  if (mode === 'AUTO') return today;
  return isSeasonModeKey(mode) ? (FIXED_SEASON[mode] ?? null) : null;
};

/** 상태 줄 문구. mode가 null이면 서버가 아직 계절 모드를 모른다(옛 서버) */
export const seasonModeSummary = (mode: string | null | undefined, today: TSeasonKey): string => {
  if (mode === null || mode === undefined) return '서버가 아직 계절 모드를 지원하지 않아요';
  if (!isSeasonModeKey(mode)) return `알 수 없는 값 '${mode}' (앱은 끄기로 보여요)`;
  if (mode === 'OFF') return '끄기 · 테마 기본 색';
  if (mode === 'AUTO') return `자동 · 지금은 ${SEASON_LABEL[today]} (${SEASON_PERIOD[today]})`;
  return SEASON_MODE_TEXT[mode].label;
};

/** 글자 대비 등급 (WCAG 2.x 본문 글자): 7 이상 AAA, 4.5 이상 AA, 그 밖 부족 */
export const contrastGrade = (ratio: number): 'AAA' | 'AA' | '부족' => {
  if (ratio >= 7) return 'AAA';
  if (ratio >= 4.5) return 'AA';
  return '부족';
};

/** 대비 값 표시 (소수 둘째 자리, 내림 — 4.499를 4.50으로 보이지 않게) */
export const fmtContrast = (ratio: number): string => `${(Math.floor(ratio * 100) / 100).toFixed(2)}:1`;
