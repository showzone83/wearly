// 단위 테스트: 확인 대화상자 스토어 (src/stores/useConfirmStore.ts, 이슈 #10).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { useConfirmStore } from '../src/stores/useConfirmStore.ts';

const state = () => useConfirmStore.getState();

test('확인하면 true, 취소하면 false로 끝나고 큐가 빈다', async () => {
  const yes = state().ask({ message: '삭제할까요?', tone: 'danger' });
  assert.equal(state().queue.length, 1);
  assert.equal(state().queue[0].options.message, '삭제할까요?');
  state().settle(state().queue[0].id, true);
  assert.equal(await yes, true);
  assert.equal(state().queue.length, 0);

  const no = state().ask({ message: '정지할까요?' });
  state().settle(state().queue[0].id, false);
  assert.equal(await no, false);
  assert.equal(state().queue.length, 0);
});

test('여러 번 부르면 순서대로 하나씩 뜬다', async () => {
  const first = state().ask({ message: '첫째' });
  const second = state().ask({ message: '둘째' });
  assert.deepEqual(
    state().queue.map((r) => r.options.message),
    ['첫째', '둘째'],
  );
  state().settle(state().queue[0].id, false);
  assert.equal(state().queue[0].options.message, '둘째');
  state().settle(state().queue[0].id, true);
  assert.deepEqual([await first, await second], [false, true]);
});

test('이미 끝난 요청을 다시 settle해도 아무 일도 없다', async () => {
  const p = state().ask({ message: '한 번만' });
  const { id } = state().queue[0];
  state().settle(id, true);
  state().settle(id, false);
  assert.equal(await p, true);
  assert.equal(state().queue.length, 0);
});
