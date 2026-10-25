// 옷장 도우미 연결 (doc/26 4단계): 앱 API 모듈 api.pairing으로 두 계정을 주인 코드로 연결·승인·권한 변경·해제한다.
// 코드는 옷장 주인만 만든다 (#18). 대리 작업(/owners/{ownerId}/…)은 api.owners로 확인한다 (5단계).
import { eq, expectApiError, ok } from '../lib/assert';
import { item } from '../lib/fixtures';
import { checkErrorBody, raw } from '../lib/http';
import { solidPng } from '../lib/png';
import { signedUp, tokensOf, type UserSession } from '../lib/session';
import type { Scenario } from '../scenario';

const CODE_RE = /^[A-HJKMNP-Z2-9]{8}$/;

/** 주인 코드 → 돕는 사람 입력 → 주인 승인. 주인 쪽에서 본 연결 */
async function connect(owner: UserSession, helper: UserSession, permission: 'VIEW' | 'EDIT') {
  const pending = await helper.api.pairing.join((await owner.api.pairing.issueCode()).code);
  return owner.api.pairing.approve(pending.id, permission);
}

const ownerCodeFlow: Scenario = {
  group: '옷장 도우미',
  name: '주인 코드 → 돕는 사람이 소문자·하이픈으로 입력(PENDING) → 1회용 → 주인 목록 → 돕는 사람은 승인 404 → 주인 승인 → 권한·알림 변경 → 작업 기록 빈 목록 → 돕는 사람이 해제 → 목록에서 사라짐',
  async run() {
    const owner = await signedUp('pair-o', '엄마');
    const helper = await signedUp('pair-h', '딸');
    const ownerId = (await owner.api.users.me()).id;

    const issued = await owner.api.pairing.issueCode();
    ok(CODE_RE.test(issued.code), '코드 8자 형식', issued.code);
    eq(issued.role, 'OWNER', '코드를 만든 자리');
    const left = Date.parse(issued.expiresAt) - Date.now();
    ok(left > 23 * 3600_000 && left <= 24 * 3600_000 + 60_000, '유효 시간 약 24시간', issued.expiresAt);

    const typed = `${issued.code.slice(0, 4)}-${issued.code.slice(4)}`.toLowerCase();
    const pending = await helper.api.pairing.join(typed);
    eq([pending.status, pending.role, pending.permission, pending.partnerId, pending.partnerName], ['PENDING', 'HELPER', null, ownerId, '엄마'], '대기 연결');
    eq(pending.issuedBy, 'OWNER', '코드를 만든 쪽');

    await expectApiError(helper.api.pairing.join(typed), { status: 404, code: 'PAIRING_CODE_INVALID' }, '1회용 코드 다시 입력');

    const ownerList = await owner.api.pairing.list();
    eq(ownerList.asOwner.map((p) => [p.partnerName, p.status]), [['딸', 'PENDING']], '주인 목록 대기');
    eq(ownerList.asHelper.length, 0, '주인은 돕는 쪽 없음');

    await expectApiError(helper.api.pairing.approve(pending.id, 'EDIT'), { status: 404, code: 'NOT_FOUND' }, '돕는 사람은 승인할 수 없다');
    const active = await owner.api.pairing.approve(pending.id, 'EDIT');
    eq([active.status, active.permission, active.role], ['ACTIVE', 'EDIT', 'OWNER'], '승인');
    await expectApiError(owner.api.pairing.approve(pending.id, 'EDIT'), { status: 404, code: 'NOT_FOUND' }, '이미 연결됨');

    const changed = await owner.api.pairing.update(pending.id, { permission: 'VIEW', notifyOwner: false });
    eq([changed.permission, changed.notifyOwner], ['VIEW', false], '권한·알림 변경');
    await expectApiError(helper.api.pairing.update(pending.id, { permission: 'EDIT' }), { status: 404, code: 'NOT_FOUND' }, '돕는 사람은 변경할 수 없다');

    eq((await helper.api.pairing.actions(pending.id)).length, 0, '작업 기록 아직 없음');
    await expectApiError(signedUp('pair-x', '남').then((x) => x.api.pairing.actions(pending.id)), { status: 404, code: 'NOT_FOUND' }, '남의 연결 기록');

    await helper.api.pairing.end(pending.id);
    eq((await owner.api.pairing.list()).asOwner.length, 0, '해제 뒤 주인 목록');
    eq((await helper.api.pairing.list()).asHelper.length, 0, '해제 뒤 돕는 사람 목록');
    await expectApiError(owner.api.pairing.end(pending.id), { status: 404, code: 'NOT_FOUND' }, '끝난 연결 해제');
  },
};

const ownerOnlyCode: Scenario = {
  group: '옷장 도우미',
  name: '돕는 사람 자리 코드는 400(#18) → 주인 코드로 연결 → 중복·자기 코드·인원 초과 오류 → 주인이 해제 → 거절 → 없는 코드',
  async run() {
    const owner = await signedUp('pair2-o', '아빠');
    const helper = await signedUp('pair2-h', '아들');

    // 코드는 옷장 주인만 만든다: 앱 모듈은 자리를 받지 않으므로 서버를 직접 부른다
    const ht = (await tokensOf(helper)).accessToken;
    const helperRole = await raw('POST', '/pairings/codes', { token: ht, body: { role: 'HELPER' } });
    checkErrorBody(helperRole, 400, 'VALIDATION_FAILED', '돕는 사람 자리 코드');
    eq(helperRole.json.fieldErrors.map((f: { field: string; code: string }) => `${f.field}:${f.code}`), ['role:NOT_ALLOWED'], '자리 값 오류');
    checkErrorBody(await raw('POST', '/pairings/codes', { token: ht, body: { role: 'FRIEND' } }), 400, 'VALIDATION_FAILED', '모르는 자리');

    const active = await connect(owner, helper, 'VIEW');
    eq([active.status, active.permission, active.issuedBy, active.partnerName], ['ACTIVE', 'VIEW', 'OWNER', '아들'], '주인 코드로 연결');

    // 같은 사이 다시: 이미 연결
    const again = (await owner.api.pairing.issueCode()).code;
    await expectApiError(helper.api.pairing.join(again), { status: 409, code: 'PAIRING_EXISTS' }, '이미 연결됨');
    // 내 코드는 내가 입력할 수 없다
    await expectApiError(owner.api.pairing.join(again), { status: 400, code: 'PAIRING_SELF' }, '자기 코드');

    // 주인은 돕는 사람 1명까지 (남은 한 칸 없음)
    const other = await signedUp('pair2-x', '삼촌');
    const full = await expectApiError(other.api.pairing.join(again), { status: 409, code: 'PAIRING_LIMIT' }, '돕는 사람 인원 초과');
    eq([full.params?.side, full.params?.max], ['OWNER', 1], '인원 초과 params');

    await owner.api.pairing.end(active.id);

    // 거절: 돕는 사람이 주인 코드를 넣고 주인이 거절
    const pending = await helper.api.pairing.join((await owner.api.pairing.issueCode()).code);
    await owner.api.pairing.reject(pending.id);
    eq((await helper.api.pairing.list()).asHelper.length, 0, '거절 뒤 목록');
    await expectApiError(owner.api.pairing.reject(pending.id), { status: 404, code: 'NOT_FOUND' }, '끝난 요청 거절');

    // 없는 코드
    await expectApiError(helper.api.pairing.join('ZZZZ-ZZZZ'), { status: 404, code: 'PAIRING_CODE_INVALID' }, '없는 코드');
  },
};

const ownerCloset: Scenario = {
  group: '옷장 도우미',
  name: '대리 작업(api.owners): 보기 권한은 조회만(등록 403) → 권한 올리면 등록·수정·사용 중지·다시 사용 → 주인 옷장에만 보임 → 주인 경로 사진 올리기·AI 분석·사진 붙여 등록 → 작업 기록 → 남·해제 뒤 404, 완전 삭제는 없음',
  async run() {
    const owner = await signedUp('own-o', '엄마');
    const helper = await signedUp('own-h', '딸');
    const stranger = await signedUp('own-x', '남');
    const ownerId = (await owner.api.users.me()).id;

    const pairing = await connect(owner, helper, 'VIEW');

    // 보기 권한: 조회만
    const seed = await owner.api.wardrobe.create(item({ name: '주인 니트' }));
    eq((await helper.api.owners.list(ownerId)).items.map((i) => i.name), ['주인 니트'], '보기 권한 목록');
    eq((await helper.api.owners.get(ownerId, seed.id)).name, '주인 니트', '보기 권한 상세');
    await expectApiError(helper.api.owners.create(ownerId, item({ name: '몰래' })), { status: 403, code: 'FORBIDDEN' }, '보기 권한으로 등록');
    await expectApiError(helper.api.owners.update(ownerId, seed.id, { name: 'x' }), { status: 403, code: 'FORBIDDEN' }, '보기 권한으로 수정');
    await expectApiError(helper.api.owners.retire(ownerId, seed.id, { reason: 'DONATED' }), { status: 403, code: 'FORBIDDEN' }, '보기 권한으로 사용 중지');
    await expectApiError(stranger.api.owners.list(ownerId), { status: 404, code: 'NOT_FOUND' }, '연결 없는 회원');

    // 등록·수정 권한
    await owner.api.pairing.update(pairing.id, { permission: 'EDIT' });
    const created = await helper.api.owners.create(ownerId, item({ name: '딸이 넣은 셔츠' }));
    eq(created.name, '딸이 넣은 셔츠', '대리 등록');
    eq((await helper.api.wardrobe.list()).items.length, 0, '돕는 사람 옷장에는 없다');
    const ownerNames = (await owner.api.wardrobe.list()).items.map((i) => i.name).sort();
    eq(ownerNames, ['딸이 넣은 셔츠', '주인 니트'], '주인 옷장에 보임');

    eq((await helper.api.owners.update(ownerId, created.id, { name: '딸이 고친 셔츠' })).name, '딸이 고친 셔츠', '대리 수정');
    const retired = await helper.api.owners.retire(ownerId, created.id, { reason: 'DONATED', memo: '기부' });
    ok(retired.retiredAt, '사용 중지', retired);
    eq((await helper.api.owners.list(ownerId, { retired: true })).items.map((i) => i.id), [created.id], '사용 안 함 목록');
    const restored = await helper.api.owners.restore(ownerId, created.id);
    eq(restored.retiredAt ?? null, null, '다시 사용');

    // 주인 경로 사진 올리기 → AI 분석 → 붙여 등록
    const png = solidPng([200, 40, 40]);
    const target = await helper.api.owners.uploadUrl(ownerId, { purpose: 'ITEM', contentType: 'image/png', size: png.length });
    await helper.api.images.put(target, new Blob([png], { type: 'image/png' }));
    const done = await helper.api.owners.complete(ownerId, target.imageId);
    eq(done.imageId, target.imageId, '사진 올리기 끝');
    await expectApiError(helper.api.wardrobe.analyzeImage({ imageId: target.imageId }), { status: 404, code: 'NOT_FOUND' }, '돕는 사람 자기 경로로는 주인 사진을 못 쓴다');
    const analyzed = await helper.api.owners.analyzeImage(ownerId, { imageId: target.imageId });
    ok(analyzed.suggestion.category, 'AI 제안', analyzed.suggestion);
    const withPhoto = await helper.api.owners.create(ownerId, { ...item({ name: '사진 셔츠' }), imageId: target.imageId });
    eq(withPhoto.image?.imageId, target.imageId, '사진이 붙음');
    const ownerView = await owner.api.wardrobe.get(withPhoto.id);
    eq(ownerView.image?.imageId, target.imageId, '주인도 같은 사진을 본다');

    // 작업 기록: 등록 2 · 수정 1 · 사용 중지 1 · 다시 사용 1
    const actions = await owner.api.pairing.actions(pairing.id);
    eq(actions.map((a) => a.action).sort(), ['ITEM_CREATE', 'ITEM_CREATE', 'ITEM_RESTORE', 'ITEM_RETIRE', 'ITEM_UPDATE'], '작업 기록');
    eq((await helper.api.pairing.actions(pairing.id)).length, 5, '돕는 사람도 같은 기록');

    // 해제하면 막히고 옷은 주인에게 남는다
    await helper.api.pairing.end(pairing.id);
    await expectApiError(helper.api.owners.list(ownerId), { status: 404, code: 'NOT_FOUND' }, '해제 뒤 접근');
    eq((await owner.api.wardrobe.list()).items.length, 3, '해제해도 옷은 주인에게 남는다');
  },
};

export const pairingScenarios: Scenario[] = [ownerCodeFlow, ownerOnlyCode, ownerCloset];
