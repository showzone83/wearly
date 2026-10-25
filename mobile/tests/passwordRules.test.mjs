// 순수 함수 단위 테스트: 비밀번호 규칙과 변경 주기 문구 (src/lib/passwordRules.ts, doc/28).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  changePasswordProblems,
  dueDateText,
  dueReminderText,
  isPasswordDueNotice,
  passwordProblem,
  postponedText,
  postponeLabel,
} from '../src/lib/passwordRules.ts';

test('비밀번호는 8~64자이고 영문과 숫자를 모두 넣는다', () => {
  assert.equal(passwordProblem('pass1234'), null);
  assert.match(passwordProblem('ab12'), /8~64자/);
  assert.match(passwordProblem('a1'.repeat(33)), /8~64자/);
  assert.match(passwordProblem('abcdefgh'), /영문과 숫자/);
  assert.match(passwordProblem('12345678'), /영문과 숫자/);
});

test('변경 화면: 지금 비밀번호·새 비밀번호 형식·같은 비밀번호·확인을 본다', () => {
  assert.deepEqual(changePasswordProblems('pass1234', 'newpass99', 'newpass99'), {});
  assert.deepEqual(Object.keys(changePasswordProblems('', 'newpass99', 'newpass99')), ['currentPassword']);
  assert.match(changePasswordProblems('pass1234', 'short', 'short').newPassword, /8~64자/);
  assert.match(changePasswordProblems('pass1234', 'pass1234', 'pass1234').newPassword, /다르게/);
  assert.match(changePasswordProblems('pass1234', 'newpass99', 'newpass98').confirm, /한 번 더/);
  // 새 비밀번호가 틀렸으면 확인 칸은 따로 탓하지 않는다
  assert.equal(changePasswordProblems('pass1234', 'short', '').confirm, undefined);
});

test('기한 알림 창은 서버 주기 일수를 쓰고 없으면 90일', () => {
  const t = dueReminderText(90);
  assert.equal(t.ok, '바꾸기');
  assert.equal(t.cancel, '90일 연장');
  assert.match(t.message, /90일이 지났어요/);
  assert.equal(dueReminderText(180).cancel, '180일 연장');
  assert.equal(dueReminderText(0).cancel, '90일 연장');
  assert.equal(postponeLabel(undefined), '90일 연장');
});

test('연장 안내는 새 기한 날짜를 알린다', () => {
  const d = new Date(2027, 0, 23, 12).toISOString();
  assert.equal(dueDateText(d), '1월 23일');
  assert.equal(postponedText(d).message, '1월 23일에 다시 알려 드릴게요.');
  assert.equal(postponedText(null).message, '다음 기한에 다시 알려 드릴게요.');
  assert.equal(dueDateText('nope'), null);
});

test('알림 data.type으로 비밀번호 변경 알림을 알아본다', () => {
  assert.equal(isPasswordDueNotice({ type: 'password-change-due' }), true);
  assert.equal(isPasswordDueNotice({ type: 'plan-d1' }), false);
  assert.equal(isPasswordDueNotice(null), false);
});
