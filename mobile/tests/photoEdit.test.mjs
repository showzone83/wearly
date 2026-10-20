// 순수 함수 단위 테스트: 옷 상세 사진 바꾸기·빼기·AI 다시 분석 (src/lib/photoEditRules.ts, 이슈 #8).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  aiBadgeFields,
  applyReanalysis,
  canOfferReanalysis,
  hasItemPhoto,
  isSmallPhoto,
  photoSheetOptions,
  photoSheetTitle,
  photoVersion,
  planPhotoChange,
  reanalysisChanges,
  SMALL_PHOTO_MIN_SIDE,
} from '../src/lib/photoEditRules.ts';

const DIR = 'file:///app/Documents/photos';
const IMG_A = { imageId: 'a', url: 'https://s/a', thumbUrl: 'https://s/a-t', expiresAt: '2026-10-18T08:00:00Z' };
const IMG_B = { imageId: 'b', url: 'https://s/b', thumbUrl: 'https://s/b-t', expiresAt: '2026-10-18T09:00:00Z' };

test('작은 사진: 긴 변이 600 미만이면 안내, 크기를 모르면 안내하지 않는다', () => {
  assert.equal(SMALL_PHOTO_MIN_SIDE, 600);
  assert.equal(isSmallPhoto(599, 300), true);
  assert.equal(isSmallPhoto(300, 599), true);
  assert.equal(isSmallPhoto(600, 100), false);
  assert.equal(isSmallPhoto(1600, 1600), false);
  assert.equal(isSmallPhoto(undefined, undefined), false);
  assert.equal(isSmallPhoto(0, 0), false);
  assert.equal(isSmallPhoto(null, 500), false);
});

test('시트 선택지: 사진이 있으면 다시 찍기·앨범·빼기, 없으면 빼기가 없다', () => {
  assert.deepEqual(
    photoSheetOptions(true).map((o) => o.action),
    ['camera', 'library', 'remove'],
  );
  assert.equal(photoSheetOptions(true)[0].label, '카메라로 다시 찍기');
  assert.equal(photoSheetOptions(true)[2].danger, true);
  assert.deepEqual(
    photoSheetOptions(false).map((o) => o.action),
    ['camera', 'library'],
  );
  assert.equal(photoSheetOptions(false)[0].label, '카메라로 찍기');
  // 쇼핑몰에서 가져오기 (doc/24): 쓸 수 있을 때만, 사진 빼기 앞
  assert.deepEqual(
    photoSheetOptions(true, true).map((o) => o.action),
    ['camera', 'library', 'mall', 'remove'],
  );
  assert.deepEqual(
    photoSheetOptions(false, true).map((o) => o.action),
    ['camera', 'library', 'mall'],
  );
  assert.equal(photoSheetOptions(false, true)[2].label, '쇼핑몰에서 가져오기');
  assert.equal(photoSheetTitle(true), '사진 바꾸기');
  assert.equal(photoSheetTitle(false), '사진 추가');
});

test('사진이 있는지: 서버 사진·기기 사진·샘플 사진', () => {
  assert.equal(hasItemPhoto({ image: IMG_A }), true);
  assert.equal(hasItemPhoto({ imageUri: `${DIR}/x.jpg` }), true);
  assert.equal(hasItemPhoto({ imageUri: 'wearly-sample://nyang_jeans' }), true);
  assert.equal(hasItemPhoto({}), false);
  assert.equal(hasItemPhoto(undefined), false);
});

test('바꾼 뒤 상태: 서버 사진으로 바꾸면 기기 사진·옮기기 기록을 지우고 폴더 안 예전 파일만 지운다', () => {
  const plan = planPhotoChange(
    { image: IMG_A, imageUri: `${DIR}/i1.jpg`, localPhoto: `${DIR}/i1.jpg` },
    { kind: 'server', image: IMG_B },
    DIR,
  );
  assert.deepEqual(plan.item, { image: IMG_B, imageUri: undefined });
  assert.equal(plan.localPhoto, undefined);
  assert.equal(plan.clearSync, true);
  assert.deepEqual(plan.deleteFiles, [`${DIR}/i1.jpg`]); // 같은 파일은 한 번만
});

test('바꾼 뒤 상태: 아직 못 옮긴 기기 사진(서버 사진 없음)을 서버 사진으로 바꾸면 옮기기 대기가 정리된다', () => {
  const plan = planPhotoChange(
    { imageUri: `${DIR}/i2.jpg`, localPhoto: `${DIR}/i2.jpg` },
    { kind: 'server', image: IMG_A },
    `${DIR}/`,
  );
  assert.deepEqual(plan.item, { image: IMG_A, imageUri: undefined });
  assert.equal(plan.localPhoto, undefined);
  assert.equal(plan.clearSync, true);
  assert.deepEqual(plan.deleteFiles, [`${DIR}/i2.jpg`]);
});

test('바꾼 뒤 상태: 게스트 기기 사진 교체는 새 파일을 쓰고 예전 파일을 지운다 (새 파일은 지우지 않는다)', () => {
  const next = `${DIR}/i3-${photoVersion(1_700_000_000_000)}.jpg`;
  const plan = planPhotoChange({ imageUri: `${DIR}/i3.jpg` }, { kind: 'local', uri: next }, DIR);
  assert.deepEqual(plan.item, { image: undefined, imageUri: next });
  assert.equal(plan.localPhoto, next);
  assert.deepEqual(plan.deleteFiles, [`${DIR}/i3.jpg`]);

  const same = planPhotoChange({ imageUri: next }, { kind: 'local', uri: next }, DIR);
  assert.deepEqual(same.deleteFiles, []);
});

test('바꾼 뒤 상태: 폴더 밖 파일·샘플·웹 주소는 지우지 않는다, 웹(폴더 없음)도 지우지 않는다', () => {
  const cache = 'file:///app/Library/Caches/ImagePicker/x.jpg';
  assert.deepEqual(planPhotoChange({ imageUri: cache }, { kind: 'removed' }, DIR).deleteFiles, []);
  assert.deepEqual(planPhotoChange({ imageUri: 'wearly-sample://nyang_jeans' }, { kind: 'removed' }, DIR).deleteFiles, []);
  assert.deepEqual(planPhotoChange({ imageUri: 'blob:http://localhost/1' }, { kind: 'removed' }, '').deleteFiles, []);
  // 이름이 비슷한 다른 폴더(photos2)도 지우지 않는다
  assert.deepEqual(planPhotoChange({ imageUri: `${DIR}2/a.jpg` }, { kind: 'removed' }, DIR).deleteFiles, []);
});

test('바꾼 뒤 상태: 빼기는 서버·기기 사진을 모두 지운다', () => {
  const plan = planPhotoChange({ image: IMG_A, imageUri: `${DIR}/i4.jpg`, localPhoto: `${DIR}/i4.jpg` }, { kind: 'removed' }, DIR);
  assert.deepEqual(plan.item, { image: undefined, imageUri: undefined });
  assert.equal(plan.localPhoto, undefined);
  assert.equal(plan.clearSync, true);
  assert.deepEqual(plan.deleteFiles, [`${DIR}/i4.jpg`]);
});

test('바꾼 뒤 상태: 예전 서버(기기 사진으로 대신)는 서버 사진 필드를 건드리지 않는다', () => {
  const plan = planPhotoChange({ localPhoto: `${DIR}/i5.jpg`, imageUri: `${DIR}/i5.jpg` }, { kind: 'local', uri: `${DIR}/i5-z.jpg` }, DIR);
  assert.deepEqual(plan.item, { image: undefined, imageUri: `${DIR}/i5-z.jpg` });
  assert.equal(plan.localPhoto, `${DIR}/i5-z.jpg`);
  assert.deepEqual(plan.deleteFiles, [`${DIR}/i5.jpg`]);
});

test('버전: 시각마다 다른 짧은 글자', () => {
  assert.notEqual(photoVersion(1), photoVersion(2));
  assert.match(photoVersion(Date.now()), /^[0-9a-z]+$/);
});

test('다시 분석 제안: 계정 모드는 서버 사진이 있을 때만, 게스트는 목업 실행에서만', () => {
  assert.equal(canOfferReanalysis({ accountMode: true, hasServerImage: true, mockMode: false }), true);
  assert.equal(canOfferReanalysis({ accountMode: true, hasServerImage: false, mockMode: true }), false);
  assert.equal(canOfferReanalysis({ accountMode: false, hasServerImage: false, mockMode: true }), true);
  assert.equal(canOfferReanalysis({ accountMode: false, hasServerImage: false, mockMode: false }), false);
});

const base = {
  name: '네이비 니트',
  category: 'TOP',
  subType: 'KNIT',
  color: 'navy',
  colors: ['navy'],
  pattern: 'SOLID',
  seasons: ['FALL', 'WINTER'],
  styles: ['NEAT'],
  formality: 2,
  status: 'OWNED',
  brand: '브랜드',
};

test('다시 분석: 바뀌는 값만 고른다 (계절·스타일은 순서 무관, 색은 순서까지)', () => {
  assert.deepEqual(reanalysisChanges(base, { ...base, seasons: ['WINTER', 'FALL'] }), []);
  assert.deepEqual(reanalysisChanges(base, { ...base, subType: 'SHIRT' }), ['kind']);
  assert.deepEqual(
    reanalysisChanges(base, { ...base, color: 'black', colors: ['black', 'white'], pattern: 'STRIPE', formality: 3 }),
    ['colors', 'pattern', 'formality'],
  );
  assert.deepEqual(reanalysisChanges({ ...base, colors: ['navy', 'white'] }, { ...base, colors: ['white', 'navy'], color: 'white' }), [
    'colors',
  ]);
  assert.deepEqual(reanalysisChanges(base, { ...base, styles: [] }), ['styles']);
  // 색 목록이 비면 주 색상 하나로 본다 (예전 데이터)
  assert.deepEqual(reanalysisChanges({ ...base, colors: [] }, base), []);
});

test('다시 분석 적용: 고른 항목만 바꾸고 이름·보유 상태·브랜드는 그대로, 주 색상은 색 목록 첫 번째', () => {
  const suggested = { ...base, name: 'AI 이름', category: 'BOTTOM', subType: 'JEANS', color: 'blue', colors: ['blue', 'white'], pattern: 'DENIM', status: 'X' };
  const fields = reanalysisChanges(base, suggested);
  assert.deepEqual(fields, ['kind', 'colors', 'pattern']);
  const next = applyReanalysis(base, suggested, fields);
  assert.equal(next.category, 'BOTTOM');
  assert.equal(next.subType, 'JEANS');
  assert.deepEqual(next.colors, ['blue', 'white']);
  assert.equal(next.color, 'blue');
  assert.equal(next.pattern, 'DENIM');
  assert.equal(next.name, '네이비 니트');
  assert.equal(next.status, 'OWNED');
  assert.equal(next.brand, '브랜드');
  assert.deepEqual(next.seasons, base.seasons);
  // 원래 값은 바뀌지 않는다
  assert.equal(base.category, 'TOP');
  assert.deepEqual(aiBadgeFields(fields), ['category', 'color', 'pattern']);
  assert.deepEqual(aiBadgeFields(['seasons', 'formality']), []);
});
