// 순수 함수 단위 테스트: 앱 설정 > 테마의 계절 모드 (src/lib/seasonMode.ts, 이슈 #6).
// 앱 계절 데이터(mobile/src/data/seasons.ts)와 모드 목록·계절 색 대비가 맞는지도 본다.
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  appliedSeason,
  contrastGrade,
  fmtContrast,
  isSeasonModeKey,
  SEASON_MODE_KEYS,
  SEASON_MODE_TEXT,
  seasonModeSummary,
} from '../src/lib/seasonMode.ts';
import { contrastIssues, mergeSeason, resolveSeason, SEASON_MODES, SEASONS } from '../../mobile/src/data/seasons.ts';
import { THEME_IDS, THEMES } from '../../mobile/src/data/themes.ts';

test('계절 모드 목록이 앱(seasons.ts)·서버와 같다', () => {
  assert.deepEqual(SEASON_MODE_KEYS, SEASON_MODES);
  assert.deepEqual(SEASON_MODE_KEYS, ['OFF', 'AUTO', 'SPRING', 'SUMMER', 'FALL', 'WINTER']);
  for (const m of SEASON_MODE_KEYS) assert.ok(SEASON_MODE_TEXT[m].label.length > 0, m);
  assert.equal(isSeasonModeKey('AUTO'), true);
  assert.equal(isSeasonModeKey('auto'), false);
  assert.equal(isSeasonModeKey(null), false);
});

test('지금 앱에 보이는 계절: 앱 resolveSeason과 같은 결과', () => {
  const oct = new Date('2026-10-18T12:00:00+09:00');
  for (const mode of SEASON_MODE_KEYS) {
    assert.equal(appliedSeason(mode, 'fall'), resolveSeason(mode, 'follow', oct), mode);
  }
  assert.equal(appliedSeason('HOLIDAY', 'fall'), null);
  assert.equal(appliedSeason(null, 'fall'), null);
});

test('상태 문구', () => {
  assert.equal(seasonModeSummary('OFF', 'fall'), '끄기 · 테마 기본 색');
  assert.equal(seasonModeSummary('AUTO', 'fall'), '자동 · 지금은 가을 (9~11월)');
  assert.equal(seasonModeSummary('AUTO', 'winter'), '자동 · 지금은 겨울 (12~2월)');
  assert.equal(seasonModeSummary('SPRING', 'fall'), '봄 고정');
  assert.equal(seasonModeSummary(null, 'fall'), '서버가 아직 계절 모드를 지원하지 않아요');
  assert.match(seasonModeSummary('HOLIDAY', 'fall'), /알 수 없는 값 'HOLIDAY'/);
});

test('대비 등급과 표시: 기준 근처는 내림으로 보인다', () => {
  assert.equal(contrastGrade(7), 'AAA');
  assert.equal(contrastGrade(6.99), 'AA');
  assert.equal(contrastGrade(4.5), 'AA');
  assert.equal(contrastGrade(4.499), '부족');
  assert.equal(fmtContrast(4.499), '4.49:1');
  assert.equal(fmtContrast(21), '21.00:1');
});

test('미리보기 8칸(4계절 × 2테마) 모두 글자 대비 AA', () => {
  for (const id of THEME_IDS) {
    for (const season of SEASONS) {
      assert.deepEqual(contrastIssues(mergeSeason(THEMES[id], season).colors), [], `${id} ${season}`);
    }
  }
});
