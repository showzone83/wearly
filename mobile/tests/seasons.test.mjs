// 순수 함수 단위 테스트: 계절 테마 (src/data/seasons.ts, 이슈 #6).
// 실행: npm run test:unit (node 내장 테스트 러너, TS는 node가 타입만 지우고 읽는다)
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  AA_TEXT,
  contrastIssues,
  contrastRatio,
  mergeSeason,
  parseSeasonMode,
  resolveSeason,
  SEASON_COLORS,
  SEASON_INFO,
  SEASON_MODES,
  SEASONS,
  seasonAt,
  seasonalTheme,
  seasonOfMonth,
  seoulDateKey,
} from '../src/data/seasons.ts';
import { THEME_IDS, THEMES } from '../src/data/themes.ts';

const SEASON_TOKENS = [
  'bg',
  'surfaceAlt',
  'border',
  'chipBorder',
  'tabBar',
  'primary',
  'primaryPressed',
  'primarySoft',
  'accent',
  'accentSoft',
];

test('월 → 계절: 3~5 봄, 6~8 여름, 9~11 가을, 12~2 겨울', () => {
  const expected = [
    'winter', 'winter', 'spring', 'spring', 'spring', 'summer',
    'summer', 'summer', 'fall', 'fall', 'fall', 'winter',
  ];
  for (let m = 1; m <= 12; m++) assert.equal(seasonOfMonth(m), expected[m - 1], `${m}월`);
});

test('경계: 한국 시각 2월 말 23:59 → 겨울, 3월 1일 0시 → 봄 (윤년 포함)', () => {
  assert.equal(seasonAt(new Date('2027-02-28T23:59:59.999+09:00')), 'winter');
  assert.equal(seasonAt(new Date('2027-03-01T00:00:00+09:00')), 'spring');
  assert.equal(seasonAt(new Date('2028-02-29T23:59:59+09:00')), 'winter');
  assert.equal(seasonAt(new Date('2028-03-01T00:00:00+09:00')), 'spring');
});

test('경계: 한국 시각 11/30 23:59 → 가을, 12/1 0시 → 겨울', () => {
  assert.equal(seasonAt(new Date('2026-11-30T23:59:59.999+09:00')), 'fall');
  assert.equal(seasonAt(new Date('2026-12-01T00:00:00+09:00')), 'winter');
  assert.equal(seoulDateKey(new Date('2026-11-30T15:00:00Z')), '2026-12-01');
});

test('기기 시간대와 상관없이 한국 날짜로 본다 (해외 사용자도 같은 계절)', () => {
  // 로스앤젤레스 8/31 20:00 = 한국 9/1 12:00 → 가을
  assert.equal(seasonAt(new Date('2026-08-31T20:00:00-07:00')), 'fall');
  // UTC 2/28 15:00 = 한국 3/1 0시 → 봄 (UTC로는 아직 2월)
  assert.equal(seasonAt(new Date('2027-02-28T15:00:00Z')), 'spring');
  assert.equal(seasonAt(new Date('2027-02-28T14:59:59Z')), 'winter');
});

test('모드·사용자 선택 → 지금 계절', () => {
  const oct = new Date('2026-10-18T12:00:00+09:00');
  assert.equal(resolveSeason('OFF', 'follow', oct), null);
  assert.equal(resolveSeason('AUTO', 'follow', oct), 'fall');
  assert.equal(resolveSeason('AUTO', 'off', oct), null, '사용자가 끄면 서버 모드와 상관없이 없음');
  assert.equal(resolveSeason('WINTER', 'follow', oct), 'winter', '고정 모드는 날짜와 상관없음');
  assert.equal(resolveSeason('SPRING', 'off', oct), null);
  assert.equal(resolveSeason('SUMMER', undefined, oct), 'summer', '기본 선택은 따르기');
});

test('서버 값 읽기: 없거나 모르는 값은 OFF', () => {
  assert.deepEqual(SEASON_MODES, ['OFF', 'AUTO', 'SPRING', 'SUMMER', 'FALL', 'WINTER']);
  assert.equal(parseSeasonMode(undefined), 'OFF');
  assert.equal(parseSeasonMode(null), 'OFF');
  assert.equal(parseSeasonMode('auto'), 'OFF');
  assert.equal(parseSeasonMode('HOLIDAY'), 'OFF');
  assert.equal(parseSeasonMode('FALL'), 'FALL');
});

test('계절 색 표: 테마 2 × 계절 4, 각 10개 토큰, #RRGGBB', () => {
  assert.deepEqual(Object.keys(SEASON_COLORS).sort(), [...THEME_IDS].sort());
  for (const id of THEME_IDS) {
    assert.deepEqual(Object.keys(SEASON_COLORS[id]).sort(), [...SEASONS].sort());
    for (const season of SEASONS) {
      const over = SEASON_COLORS[id][season];
      assert.deepEqual(Object.keys(over).sort(), [...SEASON_TOKENS].sort(), `${id} ${season}`);
      for (const v of Object.values(over)) assert.match(v, /^#[0-9A-F]{6}$/, `${id} ${season} ${v}`);
    }
  }
  for (const season of SEASONS) assert.ok(SEASON_INFO[season].greeting.length > 0);
});

test('덮어쓰기 병합: 계절 토큰만 바뀌고 나머지·원본은 그대로', () => {
  const base = THEMES.nyang;
  const before = JSON.stringify(base);
  const fall = mergeSeason(base, 'fall');
  assert.equal(JSON.stringify(base), before, '원본은 바꾸지 않는다');
  assert.notEqual(fall, base);
  assert.equal(fall.id, 'nyang');
  assert.equal(fall.mascot, 'nyang', '마스코트는 그대로');
  assert.deepEqual(fall.radius, base.radius);
  for (const [k, v] of Object.entries(fall.colors)) {
    if (SEASON_TOKENS.includes(k)) assert.equal(v, SEASON_COLORS.nyang.fall[k], k);
    else assert.equal(v, base.colors[k], `${k}는 테마 값`);
  }
  assert.equal(mergeSeason(base, null), base, '계절이 없으면 같은 객체');
});

test('seasonalTheme: 같은 테마·계절이면 같은 객체 (스타일 캐시용)', () => {
  const a = seasonalTheme(THEMES.closeta, 'winter');
  assert.equal(seasonalTheme(THEMES.closeta, 'winter'), a);
  assert.notEqual(seasonalTheme(THEMES.closeta, 'spring'), a);
  assert.equal(seasonalTheme(THEMES.closeta, null), THEMES.closeta);
});

test('대비 계산: 검정/흰색 21, 같은 색 1, 순서 무관', () => {
  assert.equal(Math.round(contrastRatio('#000000', '#FFFFFF') * 100) / 100, 21);
  assert.equal(contrastRatio('#2E6248', '#2E6248'), 1);
  assert.equal(contrastRatio('#FFFFFF', '#2E6248'), contrastRatio('#2E6248', '#FFFFFF'));
  // 기준 근처 값: #767676 / 흰색 = 4.54 (AA 통과하는 가장 밝은 회색으로 알려진 값)
  assert.ok(contrastRatio('#767676', '#FFFFFF') >= AA_TEXT);
  assert.ok(contrastRatio('#777777', '#FFFFFF') < AA_TEXT);
});

test('대비: 두 테마의 기본·4계절 모두 글자/바탕 조합이 WCAG AA 4.5:1 이상', () => {
  for (const id of THEME_IDS) {
    for (const season of [null, ...SEASONS]) {
      const issues = contrastIssues(mergeSeason(THEMES[id], season).colors);
      assert.deepEqual(
        issues.map((i) => `${i.fg}/${i.bg} ${i.ratio.toFixed(2)}`),
        [],
        `${id} ${season ?? '기본'}`,
      );
    }
  }
});
