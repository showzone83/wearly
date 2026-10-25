// 순수 함수 단위 테스트: 앱 대화상자 대기열 (src/lib/dialogQueue.ts).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { dismiss, enqueue } from '../src/lib/dialogQueue.ts';

const d = (id, title, extra = {}) => ({ id, kind: 'notify', title, okLabel: '확인', ...extra });

test('차례로 쌓이고 맨 앞이 지금 보이는 것', () => {
  let q = enqueue([], d(1, '저장했어요'));
  q = enqueue(q, d(2, '사진을 불러오지 못했어요'));
  assert.deepEqual(
    q.map((x) => x.id),
    [1, 2],
  );
});

test('닫으면 다음 것이 보이고 없는 id는 그대로', () => {
  const q = [d(1, 'a'), d(2, 'b')];
  assert.deepEqual(
    dismiss(q, 1).map((x) => x.id),
    [2],
  );
  assert.deepEqual(
    dismiss(q, 9).map((x) => x.id),
    [1, 2],
  );
});

test('같은 내용을 연달아 넣으면(연타) 하나만', () => {
  let q = enqueue([], d(1, '알림', { message: '출시 버전에서 동작해요.' }));
  q = enqueue(q, d(2, '알림', { message: '출시 버전에서 동작해요.' }));
  assert.equal(q.length, 1);
  // 내용·종류가 다르면 따로
  q = enqueue(q, d(3, '알림', { message: '다른 내용' }));
  q = enqueue(q, d(4, '알림', { kind: 'confirm', message: '다른 내용' }));
  assert.equal(q.length, 3);
});

test('원래 줄은 바꾸지 않는다', () => {
  const q = [d(1, 'a')];
  enqueue(q, d(2, 'b'));
  dismiss(q, 1);
  assert.equal(q.length, 1);
});
