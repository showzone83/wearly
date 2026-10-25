// 순수 함수 단위 테스트: 탈퇴 유예 규칙 (src/lib/withdrawRules.ts, doc/27).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  cancelWithdrawalText,
  purgeDateText,
  REASON_TEXT_MAX,
  WITHDRAW_REASONS,
  withdrawDoneText,
  withdrawRequest,
} from '../src/lib/withdrawRules.ts';

test('사유 보기는 서버 값 6개이고 겹치지 않는다', () => {
  assert.deepEqual(
    WITHDRAW_REASONS.map((r) => r.value),
    ['NOT_USEFUL', 'BAD_RECOMMEND', 'HARD_TO_USE', 'PRIVACY', 'OTHER_APP', 'OTHER'],
  );
  assert.equal(new Set(WITHDRAW_REASONS.map((r) => r.label)).size, 6);
});

test('사유가 없으면 보낼 수 없고, 하고 싶은 말은 공백을 지우고 비면 뺀다', () => {
  assert.equal(withdrawRequest(null, '안녕'), null);
  assert.deepEqual(withdrawRequest('OTHER', '   '), { reason: 'OTHER' });
  assert.deepEqual(withdrawRequest('PRIVACY', '  걱정돼요 '), { reason: 'PRIVACY', reasonText: '걱정돼요' });
  assert.equal(withdrawRequest('OTHER', '가'.repeat(250)).reasonText.length, REASON_TEXT_MAX);
});

test('삭제 예정일은 기기 시간대의 "M월 D일", 읽을 수 없으면 null', () => {
  const iso = new Date(2026, 10, 24, 15, 0).toISOString(); // 기기 시간 11월 24일 15시
  assert.equal(purgeDateText(iso), '11월 24일');
  assert.equal(purgeDateText('nope'), null);
  assert.equal(purgeDateText(undefined), null);
});

test('탈퇴 뒤 안내와 로그인 취소 창은 날짜가 없어도 문장이 된다', () => {
  const iso = new Date(2026, 10, 24, 15, 0).toISOString();
  assert.match(withdrawDoneText(iso).message, /^11월 24일에 모든 정보가 삭제돼요/);
  assert.match(withdrawDoneText(null).message, /^30일 뒤 모든 정보가 삭제돼요/);
  assert.match(cancelWithdrawalText(iso).message, /^11월 24일에 삭제될 예정인 계정이에요/);
  assert.match(cancelWithdrawalText(42).message, /^삭제될 예정인 계정이에요/);
});
