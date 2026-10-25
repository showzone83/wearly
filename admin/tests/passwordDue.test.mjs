// 비밀번호 변경 주기 화면의 순수 함수(src/lib/passwordDue.ts, doc/28). 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  dueDateText,
  nextChangeText,
  passwordDueBannerText,
  postponedToastText,
  postponeLabel,
} from '../src/lib/passwordDue.ts';

const iso = new Date(2027, 0, 23, 12).toISOString();

test('기한이 지났을 때만 안내 띠를 띄우고 서버 주기 일수를 쓴다', () => {
  assert.equal(passwordDueBannerText(null), null);
  assert.equal(passwordDueBannerText({ dueAt: iso, due: false, intervalDays: 90 }), null);
  assert.match(passwordDueBannerText({ dueAt: iso, due: true, intervalDays: 90 }), /90일이 지났어요/);
  assert.match(passwordDueBannerText({ dueAt: iso, due: true, intervalDays: 180 }), /180일이 지났어요/);
  assert.equal(postponeLabel(0), '90일 연장');
  assert.equal(postponeLabel(30), '30일 연장');
});

test('연장 토스트와 다음에 바꿀 날', () => {
  assert.equal(dueDateText(iso), '1월 23일');
  assert.equal(dueDateText('bad'), null);
  assert.equal(postponedToastText(iso), '비밀번호 변경 알림을 미뤘어요. 1월 23일에 다시 알려 드릴게요.');
  assert.equal(postponedToastText(null), '비밀번호 변경 알림을 미뤘어요.');
  assert.equal(nextChangeText({ dueAt: iso, due: false, intervalDays: 90 }), '다음에 바꿀 날: 1월 23일');
  assert.match(nextChangeText({ dueAt: iso, due: true, intervalDays: 90 }), /지금 바꿔 주세요/);
  assert.equal(nextChangeText({ dueAt: null, due: false, intervalDays: 0 }), null);
});
