// 순수 함수 단위 테스트: 모델 드롭다운(src/lib/modelPicker.ts).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  filterModels,
  findModel,
  initialMode,
  isUnlisted,
  moveIndex,
  sourceText,
  UNSAVED_SOURCE_TEXT,
} from '../src/lib/modelPicker.ts';

const models = [
  { id: 'claude-sonnet-5-5', displayName: 'Claude Sonnet 5.5', createdAt: null, recommended: true },
  { id: 'claude-haiku-5-5', displayName: 'Claude Haiku 5.5', createdAt: null, recommended: false },
  { id: 'claude-opus-5-5', displayName: 'Claude Opus 5.5', createdAt: null, recommended: false },
];

test('모델 찾기와 처음 모드', () => {
  assert.equal(findModel(models, ' claude-opus-5-5 ')?.displayName, 'Claude Opus 5.5');
  assert.equal(findModel(models, 'nope'), null);
  assert.equal(findModel(models, ''), null);
  assert.equal(findModel(models, null), null);

  // 값이 없거나 목록에 있으면 목록에서 고르기
  assert.equal(initialMode(models, ''), 'select');
  assert.equal(initialMode(models, null), 'select');
  assert.equal(initialMode(models, 'claude-sonnet-5-5'), 'select');
  // 목록에 없는 값이면 직접 입력 + "목록에 없는 모델"
  assert.equal(initialMode(models, 'claude-preview-x'), 'manual');
  assert.equal(isUnlisted(models, 'claude-preview-x'), true);
  assert.equal(isUnlisted(models, 'claude-sonnet-5-5'), false);
  assert.equal(isUnlisted(models, ''), false);
  // 목록이 비었으면(예: OPENAI 내장 목록) 직접 입력, 안내는 띄우지 않는다
  assert.equal(initialMode([], ''), 'manual');
  assert.equal(initialMode([], 'gpt-5.1'), 'manual');
  assert.equal(isUnlisted([], 'gpt-5.1'), false);
});

test('검색: ID·화면 이름, 대소문자 무시, 순서 유지', () => {
  assert.deepEqual(
    filterModels(models, 'HAIKU').map((m) => m.id),
    ['claude-haiku-5-5'],
  );
  assert.deepEqual(
    filterModels(models, 'opus-5').map((m) => m.id),
    ['claude-opus-5-5'],
  );
  assert.deepEqual(
    filterModels(models, '  ').map((m) => m.id),
    models.map((m) => m.id),
  );
  assert.deepEqual(filterModels(models, 'gemini'), []);
});

test('출처 문구', () => {
  assert.equal(sourceText({ source: 'LIVE', error: null }, '3분 전'), '제공사에서 받은 목록 · 3분 전');
  assert.equal(
    sourceText(
      { source: 'CATALOG', error: { code: 'NO_API_KEY', message: '이 공급자에 API 키가 없어요.' } },
      '방금 전',
    ),
    '내장 목록(키가 없거나 목록을 받지 못했어요: 이 공급자에 API 키가 없어요.)',
  );
  assert.equal(sourceText({ source: 'CATALOG', error: null }, '방금 전'), '내장 목록');
  assert.match(UNSAVED_SOURCE_TEXT, /^내장 목록/);
});

test('키보드 강조 이동은 처음·끝에서 돌아간다', () => {
  assert.equal(moveIndex(-1, 1, 4), 0);
  assert.equal(moveIndex(-1, -1, 4), 3);
  assert.equal(moveIndex(3, 1, 4), 0);
  assert.equal(moveIndex(0, -1, 4), 3);
  assert.equal(moveIndex(1, 1, 4), 2);
  assert.equal(moveIndex(0, 1, 0), -1);
});
