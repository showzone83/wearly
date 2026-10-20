// 순수 함수 단위 테스트: 쇼핑몰 캡처로 옷 사진 등록 (src/lib/mallCaptureRules.ts, doc/24).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  clampFrame,
  cropRect,
  frameRect,
  isAllowedNavigation,
  isHttpsUrl,
  mallList,
  MIN_FRAME_SIDE,
  moveFrame,
  resizeFrame,
} from '../src/lib/mallCaptureRules.ts';

const code = (c, active, url) => ({ code: c, label: c + ' 몰', active, attrs: url === undefined ? {} : { url } });

test('쇼핑몰 목록: 활성이고 https 주소가 있는 것만 받은 순서대로', () => {
  const list = mallList([
    code('A', true, 'https://a.kr'),
    code('OFF', false, 'https://off.kr'),
    code('HTTP', true, 'http://plain.kr'),
    code('NONE', true, undefined),
    code('USER', true, 'https://me@phish.kr'),
    code('B', true, 'https://m.b.com/shop'),
  ]);
  assert.deepEqual(list, [
    { code: 'A', label: 'A 몰', url: 'https://a.kr' },
    { code: 'B', label: 'B 몰', url: 'https://m.b.com/shop' },
  ]);
  assert.equal(isHttpsUrl(1), false);
  assert.equal(isHttpsUrl('https://'), false);
});

test('이동 제한: 웹 주소만 열고 다른 앱을 여는 주소는 막는다', () => {
  for (const ok of [
    'https://www.musinsa.com/x',
    'http://a.kr',
    'about:blank',
    'data:text/html,hi',
    'blob:https://a.kr/1',
  ]) {
    assert.equal(isAllowedNavigation(ok), true, ok);
  }
  for (const no of [
    'intent://scan/#Intent;scheme=musinsa;end',
    'market://details?id=x',
    'kakaotalk://open',
    'musinsa://product/1',
    'tel:01000000000',
    'mailto:a@b.c',
    'javascript:alert(1)',
  ]) {
    assert.equal(isAllowedNavigation(no), false, no);
  }
});

test('틀: 화면 가운데 정사각, 짧은 변에서 양쪽 여백을 뺀 크기', () => {
  assert.deepEqual(frameRect({ width: 390, height: 600 }), { x: 24, y: 24 + (552 - 342) / 2, width: 342, height: 342 });
  assert.deepEqual(frameRect({ width: 30, height: 30 }), { x: 15, y: 15, width: 0, height: 0 });
});

const VIEW = { width: 390, height: 600 };
const BOX = { x: 100, y: 200, width: 200, height: 200 };

test('틀 크기: 모서리마다 마주 보는 모서리를 고정하고 정사각을 유지한다', () => {
  // 아래오른쪽: 바깥(+x, +y)으로 끌면 커진다. 평균만큼 (20+40)/2 = 30
  assert.deepEqual(resizeFrame(BOX, 'br', 20, 40, VIEW), { x: 100, y: 200, width: 230, height: 230 });
  // 위왼쪽: 바깥(-x, -y)으로 끌면 커지고 아래오른쪽(300, 400)이 그대로
  assert.deepEqual(resizeFrame(BOX, 'tl', -30, -30, VIEW), { x: 70, y: 170, width: 230, height: 230 });
  // 위오른쪽: +x·-y가 바깥, 왼쪽 아래(100, 400) 고정
  assert.deepEqual(resizeFrame(BOX, 'tr', 10, -10, VIEW), { x: 100, y: 190, width: 210, height: 210 });
  // 아래왼쪽: 안쪽(+x, -y)으로 끌면 작아지고 오른쪽 위(300, 200) 고정
  assert.deepEqual(resizeFrame(BOX, 'bl', 50, -50, VIEW), { x: 150, y: 200, width: 150, height: 150 });
});

test('틀 크기: 최소 크기와 화면 끝에서 멈춘다', () => {
  assert.equal(MIN_FRAME_SIDE, 120);
  assert.deepEqual(resizeFrame(BOX, 'br', -500, -500, VIEW), { x: 100, y: 200, width: 120, height: 120 });
  // 아래오른쪽은 오른쪽 끝(390 - 100 = 290)까지
  assert.deepEqual(resizeFrame(BOX, 'br', 500, 500, VIEW), { x: 100, y: 200, width: 290, height: 290 });
  // 위왼쪽은 왼쪽·위 중 가까운 끝(300)까지
  assert.deepEqual(resizeFrame(BOX, 'tl', -500, -500, VIEW), { x: 0, y: 100, width: 300, height: 300 });
  // 고정 모서리가 화면 끝에 붙어 min보다 작게만 들어가면 들어가는 만큼
  const edge = { x: 300, y: 0, width: 90, height: 90 };
  assert.deepEqual(resizeFrame(edge, 'br', 100, 100, VIEW), { x: 300, y: 0, width: 90, height: 90 });
});

test('틀 위치: 크기는 그대로, 화면 밖으로 나가지 않는다', () => {
  assert.deepEqual(moveFrame(BOX, 30, -50, VIEW), { x: 130, y: 150, width: 200, height: 200 });
  assert.deepEqual(moveFrame(BOX, -999, 999, VIEW), { x: 0, y: 400, width: 200, height: 200 });
  assert.deepEqual(moveFrame(BOX, 999, -999, VIEW), { x: 190, y: 0, width: 200, height: 200 });
});

test('틀 화면 맞춤: 화면이 줄면 한 변을 줄이고 화면 안으로 옮긴다', () => {
  assert.deepEqual(clampFrame(BOX, VIEW), BOX);
  assert.deepEqual(clampFrame({ x: 300, y: 500, width: 200, height: 200 }, VIEW), { x: 190, y: 400, width: 200, height: 200 });
  assert.deepEqual(clampFrame({ x: 50, y: 50, width: 400, height: 400 }, { width: 300, height: 500 }), {
    x: 0,
    y: 50,
    width: 300,
    height: 300,
  });
  assert.deepEqual(clampFrame(BOX, { width: 0, height: 0 }), { x: 0, y: 0, width: 0, height: 0 });
});

test('자르기: 화면 좌표를 캡처 이미지 배율(3배)로 바꾼다', () => {
  const view = { width: 390, height: 600 };
  const frame = frameRect(view);
  const rect = cropRect(frame, view, { width: 1170, height: 1800 });
  assert.deepEqual(rect, { x: 72, y: 387, width: 1026, height: 1026 });
});

test('자르기: 이미지 밖으로 나가지 않게 줄이고, 크기를 모르면 null', () => {
  const rect = cropRect(
    { x: 300, y: 500, width: 200, height: 200 },
    { width: 390, height: 600 },
    { width: 390, height: 600 },
  );
  assert.deepEqual(rect, { x: 300, y: 500, width: 90, height: 100 });
  assert.equal(
    cropRect({ x: 0, y: 0, width: 1, height: 1 }, { width: 0, height: 600 }, { width: 10, height: 10 }),
    null,
  );
  assert.equal(cropRect({ x: 0, y: 0, width: 1, height: 1 }, { width: 10, height: 10 }, { width: 0, height: 0 }), null);
});
