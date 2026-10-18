// 옷 사진 서버 저장 (doc/17): 업로드 URL → 서명 URL로 바로 PUT → complete → 아이템에 붙이기 → 서명 URL로 받기,
// 잘못된 파일, 남의 이미지, 다른 아이템에 붙은 이미지. 앱 API 모듈을 거치지 않고 원시 HTTP(raw)로만 확인한다.
import { eq, isIsoDateTime, isNonEmptyString, ok } from '../lib/assert';
import { config } from '../lib/config';
import { checkErrorBody } from '../lib/http';
import { solidPng } from '../lib/png';
import { rawSignup, type RawUser } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const ITEMS = '/wardrobe/items';
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function itemBody(imageId?: string | null) {
  return {
    name: '사진 있는 셔츠',
    category: 'TOP',
    subType: 'SHIRT',
    color: 'white',
    pattern: 'SOLID',
    seasons: ['SPRING'],
    styles: ['NEAT'],
    formality: 2,
    status: 'OWNED',
    brand: null,
    ...(imageId === undefined ? {} : { imageId }),
  };
}

interface UploadUrl {
  imageId: string;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresAt: string;
}

async function uploadUrl(u: RawUser, contentType: string, size: number): Promise<UploadUrl> {
  const res = await u.call('POST', '/images/upload-url', { body: { purpose: 'ITEM', contentType, size } });
  eq(res.status, 200, `upload-url 200 (${contentType})`);
  return res.json;
}

/** 받은 서명 URL로 저장소에 바로 PUT (받은 headers를 그대로 붙인다) */
async function putTo(target: UploadUrl, bytes: Uint8Array<ArrayBuffer>): Promise<number> {
  const res = await fetch(target.uploadUrl, { method: target.method, headers: target.headers, body: bytes });
  await res.arrayBuffer();
  return res.status;
}

/** upload-url → PUT → complete. complete 응답 */
async function uploadReady(u: RawUser, bytes: Uint8Array<ArrayBuffer>, contentType = 'image/png') {
  const url = await uploadUrl(u, contentType, bytes.length);
  eq(await putTo(url, bytes), 200, '서명 URL로 PUT 200');
  const done = await u.call('POST', `/images/${url.imageId}/complete`);
  eq(done.status, 200, 'complete 200');
  return done.json as { imageId: string; url: string; thumbUrl: string; width: number; height: number; expiresAt: string };
}

async function download(url: string): Promise<{ status: number; type: string; bytes: Uint8Array }> {
  const res = await fetch(url);
  return { status: res.status, type: res.headers.get('content-type') ?? '', bytes: new Uint8Array(await res.arrayBuffer()) };
}

const flow: Scenario = {
  group: '사진 저장',
  name: 'upload-url → 서명 URL로 PUT → complete(JPEG로 다시 인코딩) → 아이템에 imageId → image.url·thumbUrl로 받기 → 사진 빼기',
  async run() {
    const u = await rawSignup('img');
    const png = solidPng([200, 40, 60], 64);
    const url = await uploadUrl(u, 'image/png', png.length);
    eq(Object.keys(url).sort(), ['expiresAt', 'headers', 'imageId', 'method', 'uploadUrl'], 'upload-url 응답 키');
    ok(UUID_RE.test(url.imageId), 'imageId는 UUID 문자열', url.imageId);
    eq(url.method, 'PUT', 'method');
    eq(url.headers, { 'Content-Type': 'image/png' }, 'headers는 Content-Type 하나');
    ok(isIsoDateTime(url.expiresAt), 'expiresAt은 ISO 시각', url.expiresAt);
    const host = new URL(config.origin).hostname;
    ok(new URL(url.uploadUrl).hostname === host, `서명 URL 호스트는 요청 Host 이름(${host})`, url.uploadUrl);

    // 올리기 전 complete는 IMAGE_NOT_UPLOADED
    checkErrorBody(await u.call('POST', `/images/${url.imageId}/complete`), 400, 'IMAGE_NOT_UPLOADED', '올리기 전 complete');
    eq(await putTo(url, png), 200, '서명 URL로 PUT 200');
    const done = await u.call('POST', `/images/${url.imageId}/complete`);
    eq(done.status, 200, 'complete 200');
    eq(Object.keys(done.json).sort(), ['expiresAt', 'height', 'imageId', 'thumbUrl', 'url', 'width'], 'complete 응답 키');
    eq([done.json.imageId, done.json.width, done.json.height], [url.imageId, 64, 64], 'complete imageId·가로·세로');

    const original = await download(done.json.url);
    eq(original.status, 200, 'url GET 200');
    eq([original.bytes[0], original.bytes[1]], [0xff, 0xd8], 'original은 JPEG로 다시 인코딩');
    eq((await download(done.json.thumbUrl)).status, 200, 'thumbUrl GET 200');
    // 버킷은 비공개: 서명을 떼면 403
    eq((await download(done.json.url.split('?')[0])).status, 403, '서명 없는 URL은 403');

    const created = await u.call('POST', ITEMS, { body: itemBody(url.imageId) });
    eq(created.status, 201, 'imageId로 아이템 등록 201');
    const image = created.json.image;
    eq(Object.keys(image).sort(), ['expiresAt', 'imageId', 'thumbUrl', 'url'], '아이템 image 키');
    eq(image.imageId, url.imageId, '아이템 image.imageId');
    ok(isNonEmptyString(image.url) && isIsoDateTime(image.expiresAt), 'image.url·expiresAt', image);
    eq((await download(image.url)).status, 200, '아이템 image.url GET 200');

    const list = await u.call('GET', ITEMS);
    eq(list.json.items[0].image?.imageId, url.imageId, '목록에도 image');
    const plain = await u.call('POST', ITEMS, { body: itemBody() });
    eq(plain.json.image, null, '사진 없는 아이템은 image: null');

    const removed = await u.call('PATCH', `${ITEMS}/${created.json.id}`, { body: { imageId: null } });
    eq(removed.status, 200, 'PATCH imageId: null 200');
    eq(removed.json.image, null, '사진을 빼면 image: null');
    eq((await download(image.url)).status, 404, '뺀 사진의 객체는 지워진다 (예전 서명 URL은 404)');
  },
};

const invalid: Scenario = {
  group: '사진 저장',
  name: '잘못된 파일: 글자 파일·가짜 JPEG는 complete에서 400 IMAGE_INVALID, upload-url 입력 오류는 400 VALIDATION_FAILED',
  async run() {
    const u = await rawSignup('img-bad');
    const text = new TextEncoder().encode('this is not an image');
    const url = await uploadUrl(u, 'image/png', text.length);
    eq(await putTo(url, Uint8Array.from(text)), 200, '글자 파일 PUT 200 (저장소는 내용을 모른다)');
    checkErrorBody(await u.call('POST', `/images/${url.imageId}/complete`), 400, 'IMAGE_INVALID', '글자 파일 complete');
    checkErrorBody(await u.call('POST', `/images/${url.imageId}/complete`), 404, 'NOT_FOUND', '실패한 이미지 다시 complete');

    const fake = new Uint8Array(1024);
    fake.set([0xff, 0xd8, 0xff, 0xe0]);
    const url2 = await uploadUrl(u, 'image/jpeg', fake.length);
    await putTo(url2, fake);
    checkErrorBody(await u.call('POST', `/images/${url2.imageId}/complete`), 400, 'IMAGE_INVALID', '가짜 JPEG complete');

    const bad = await u.call('POST', '/images/upload-url', { body: { purpose: 'ITEM', contentType: 'image/gif', size: 10 } });
    checkErrorBody(bad, 400, 'VALIDATION_FAILED', 'GIF upload-url');
    eq(bad.json.fieldErrors[0].field, 'contentType', 'GIF → contentType 오류');
    const big = await u.call('POST', '/images/upload-url', {
      body: { purpose: 'ITEM', contentType: 'image/jpeg', size: 10 * 1024 * 1024 + 1 },
    });
    checkErrorBody(big, 400, 'VALIDATION_FAILED', '10MB 초과 upload-url');
    eq(big.json.fieldErrors[0].field, 'size', '10MB 초과 → size 오류');
  },
};

const others: Scenario = {
  group: '사진 저장',
  name: '남의 이미지는 404 (complete·아이템 등록·AI 분석), 내 imageId로 AI 분석 200, PENDING은 IMAGE_NOT_UPLOADED',
  async run() {
    const owner = await rawSignup('img-own');
    const other = await rawSignup('img-other');
    const done = await uploadReady(owner, solidPng([10, 120, 200], 32));

    checkErrorBody(await other.call('POST', `/images/${done.imageId}/complete`), 404, 'NOT_FOUND', '남의 이미지 complete');
    checkErrorBody(await other.call('POST', ITEMS, { body: itemBody(done.imageId) }), 404, 'NOT_FOUND', '남의 imageId로 등록');
    checkErrorBody(
      await other.call('POST', `${ITEMS}/analyze`, { body: { imageId: done.imageId } }),
      404,
      'NOT_FOUND',
      '남의 imageId로 AI 분석',
    );

    const analyzed = await owner.call('POST', `${ITEMS}/analyze`, { body: { imageId: done.imageId } });
    eq(analyzed.status, 200, '내 imageId로 AI 분석 200');
    eq(analyzed.json.provider, 'mock', '하네스는 목업 공급자');
    ok(isNonEmptyString(analyzed.json.suggestion?.category), '분석 결과 suggestion', analyzed.json);

    const pending = await uploadUrl(owner, 'image/jpeg', 100);
    checkErrorBody(await owner.call('POST', ITEMS, { body: itemBody(pending.imageId) }), 400, 'IMAGE_NOT_UPLOADED', 'PENDING으로 등록');
  },
};

const inUse: Scenario = {
  group: '사진 저장',
  name: '다른 아이템에 붙은 이미지는 409 IMAGE_IN_USE, 사진을 바꾸면 예전 사진 객체를 지운다',
  async run() {
    const u = await rawSignup('img-use');
    const first = await uploadReady(u, solidPng([0, 0, 0], 16));
    const second = await uploadReady(u, solidPng([255, 255, 255], 16));
    const a = await u.call('POST', ITEMS, { body: itemBody(first.imageId) });
    eq(a.status, 201, '아이템 A 등록');
    checkErrorBody(await u.call('POST', ITEMS, { body: itemBody(first.imageId) }), 409, 'IMAGE_IN_USE', '같은 사진으로 B 등록');

    const changed = await u.call('PATCH', `${ITEMS}/${a.json.id}`, { body: { imageId: second.imageId } });
    eq(changed.status, 200, 'A 사진 바꾸기 200');
    eq(changed.json.image.imageId, second.imageId, '새 사진');
    eq((await download(a.json.image.url)).status, 404, '예전 사진 객체는 지워진다');
  },
};

export const imageScenarios: Scenario[] = [flow, invalid, others, inUse];
