// 순수 함수 단위 테스트: 옷장 도우미 규칙 (src/lib/pairingRules.ts, doc/26).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  endConfirmText,
  expiresInText,
  formatPairingCode,
  helperNoticeRoute,
  isCompleteCode,
  isHelperNotice,
  pairingStatusText,
} from '../src/lib/pairingRules.ts';

const pairing = (over) => ({
  id: '1',
  role: 'OWNER',
  partnerId: '2',
  partnerName: '민지',
  permission: 'EDIT',
  status: 'ACTIVE',
  issuedBy: 'HELPER',
  notifyOwner: true,
  createdAt: '2026-10-25T03:00:00.000Z',
  approvedAt: '2026-10-25T03:00:00.000Z',
  ...over,
});

test('코드 입력은 대문자·8자·4자 뒤 하이픈으로 정리한다', () => {
  assert.equal(formatPairingCode('ab'), 'AB');
  assert.equal(formatPairingCode('abcdef'), 'ABCD-EF');
  assert.equal(formatPairingCode(' abcd-efgh '), 'ABCD-EFGH');
  assert.equal(formatPairingCode('abcdefghijk'), 'ABCD-EFGH');
  assert.equal(isCompleteCode('ABCD-EFGH'), true);
  assert.equal(isCompleteCode('ABCD-EFG'), false);
});

test('코드 남은 시간', () => {
  const now = Date.parse('2026-10-25T03:00:00Z');
  assert.equal(expiresInText('2026-10-26T02:59:00Z', now), '23시간 59분 남음');
  assert.equal(expiresInText('2026-10-25T04:00:00Z', now), '1시간 남음');
  assert.equal(expiresInText('2026-10-25T03:45:30Z', now), '45분 남음');
  assert.equal(expiresInText('2026-10-25T03:00:30Z', now), '곧 사라져요');
  assert.equal(expiresInText('2026-10-25T03:00:00Z', now), '시간이 지났어요');
  assert.equal(expiresInText('이상한 값', now), '시간이 지났어요');
});

test('연결 요약과 해제 확인 문구', () => {
  assert.equal(pairingStatusText(pairing({})), '등록·수정');
  assert.equal(pairingStatusText(pairing({ permission: 'VIEW' })), '보기만');
  assert.equal(pairingStatusText(pairing({ status: 'PENDING', permission: null })), '승인을 기다려요');
  assert.equal(
    pairingStatusText(pairing({ status: 'PENDING', permission: null, role: 'HELPER' })),
    '주인이 승인하길 기다려요',
  );
  assert.equal(endConfirmText(pairing({ status: 'PENDING', permission: null })).ok, '취소하기');
  assert.match(endConfirmText(pairing({})).message, /주인 옷장에 그대로 남아요/);
});

test('옷장 도우미 알림만 도우미 화면을 연다', () => {
  for (const type of [
    'closet-pairing-request',
    'closet-pairing-accepted',
    'closet-pairing-rejected',
    'closet-pairing-ended',
    'closet-helper-digest',
  ]) {
    assert.equal(isHelperNotice({ type }), true, type);
    assert.equal(helperNoticeRoute({ type, pairingId: '3' }), '/helper');
  }
  assert.equal(isHelperNotice({ type: 'plan-d1' }), false);
  assert.equal(helperNoticeRoute(null), null);
  assert.equal(helperNoticeRoute({}), null);
  assert.equal(helperNoticeRoute({ type: 7 }), null);
});
