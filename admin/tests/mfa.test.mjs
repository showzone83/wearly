// 2단계 인증 화면의 순수 함수(src/lib/mfa.ts). 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  formatSecret,
  isTotpCode,
  lowRecoveryCodesText,
  normalizeRecoveryCode,
  normalizeTotp,
  passwordRuleError,
  recoveryCodesFileText,
} from '../src/lib/mfa.ts';

test('비밀은 4자씩 띄어 쓴다', () => {
  assert.equal(formatSecret('jbswy3dpehpk3pxp'), 'JBSW Y3DP EHPK 3PXP');
  assert.equal(formatSecret('ABCDE F'), 'ABCD EF');
  assert.equal(formatSecret(''), '');
});

test('인증 코드는 숫자 6자리만', () => {
  assert.equal(normalizeTotp('123 456'), '123456');
  assert.equal(normalizeTotp('12-34-5678'), '123456');
  assert.ok(isTotpCode('012345'));
  assert.ok(!isTotpCode('12345'));
  assert.ok(!isTotpCode('12345a'));
  assert.equal(normalizeRecoveryCode('  ABCDE-FGHJK '), 'abcde-fghjk');
});

test('비밀번호 규칙: 8~64자, 영문+숫자', () => {
  assert.equal(passwordRuleError('abc12345'), null);
  assert.match(passwordRuleError('abc1234'), /8~64자/);
  assert.match(passwordRuleError('a1'.repeat(33)), /8~64자/);
  assert.match(passwordRuleError('abcdefgh'), /영문과 숫자/);
  assert.match(passwordRuleError('12345678'), /영문과 숫자/);
});

test('복구 코드 파일과 남은 개수 경고', () => {
  const text = recoveryCodesFileText(['aaaaa-bbbbb', 'ccccc-ddddd'], 'admin@wearly.app', '2026-10-18 10:00');
  assert.match(text, /계정: admin@wearly\.app/);
  assert.match(text, / 1\. aaaaa-bbbbb\n 2\. ccccc-ddddd/);
  assert.equal(lowRecoveryCodesText(4), null);
  assert.equal(lowRecoveryCodesText(null), null);
  assert.match(lowRecoveryCodesText(3), /3개 남았어요/);
  assert.match(lowRecoveryCodesText(0), /모두 썼어요/);
});
