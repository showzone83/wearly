// 순수 함수 단위 테스트: 회원 나의 정보 표시(src/lib/userInfo.ts).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { personalParts, personalSummary } from '../src/lib/userInfo.ts';

const LABELS = {
  'AGE_RANGE:AGE_30_EARLY': '30대 초반',
  'BODY_TYPE:WAVE': '웨이브',
  'FIT:SLIM': '슬림핏',
};
const label = (group, code) => LABELS[`${group}:${code}`] ?? code;

const user = (extra) => ({
  id: '1',
  email: 'a@b.c',
  name: '테스터',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-10-18T00:00:00Z',
  itemCount: 0,
  aiToday: 0,
  ...extra,
});

test('입력된 항목만 라벨로 순서대로 보여 준다', () => {
  const u = user({ ageRange: 'AGE_30_EARLY', bodyType: 'WAVE', heightCm: 162, fitPreference: 'SLIM' });
  assert.equal(personalSummary(u, label), '30대 초반 · 웨이브 · 162cm · 슬림핏');
  assert.deepEqual(
    personalParts(u, label).map((p) => p.name),
    ['연령대', '체형', '키', '핏 선호'],
  );
});

test('모르는 코드는 코드 그대로, 일부만 있으면 그것만', () => {
  assert.equal(personalSummary(user({ bodyType: 'NEW_TYPE', heightCm: null }), label), 'NEW_TYPE');
  assert.equal(personalSummary(user({ heightCm: 175 }), label), '175cm');
});

test('비어 있거나 예전 서버(필드 없음)면 null', () => {
  assert.equal(personalSummary(user({}), label), null);
  assert.equal(
    personalSummary(user({ ageRange: null, bodyType: null, heightCm: null, fitPreference: null }), label),
    null,
  );
});
