// 옷장 도우미 연결 (doc/26 4단계): 앱 API 모듈 api.pairing으로 두 계정을 코드 두 흐름으로 연결·승인·권한 변경·해제한다.
// 대리 작업(/owners/{ownerId}/…)은 백엔드 통합 테스트가 본다 (하네스 앱 API 모듈에는 아직 없다).
import { eq, expectApiError, ok } from '../lib/assert';
import { signedUp } from '../lib/session';
import type { Scenario } from '../scenario';

const CODE_RE = /^[A-HJKMNP-Z2-9]{8}$/;

const ownerCodeFlow: Scenario = {
  group: '옷장 도우미',
  name: '주인 코드 → 돕는 사람이 소문자·하이픈으로 입력(PENDING) → 1회용 → 주인 목록 → 돕는 사람은 승인 404 → 주인 승인 → 권한·알림 변경 → 작업 기록 빈 목록 → 돕는 사람이 해제 → 목록에서 사라짐',
  async run() {
    const owner = await signedUp('pair-o', '엄마');
    const helper = await signedUp('pair-h', '딸');
    const ownerId = (await owner.api.users.me()).id;

    const issued = await owner.api.pairing.issueCode('OWNER');
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

const helperCodeFlow: Scenario = {
  group: '옷장 도우미',
  name: '돕는 사람 코드 → 주인이 권한 없이 입력 400 → 권한으로 입력(바로 ACTIVE) → 자기 코드·중복·인원 초과 오류 → 거절 → 주인이 해제',
  async run() {
    const owner = await signedUp('pair2-o', '아빠');
    const helper = await signedUp('pair2-h', '아들');

    const code = (await helper.api.pairing.issueCode('HELPER')).code;
    const bad = await expectApiError(owner.api.pairing.join(code), { status: 400, code: 'VALIDATION_FAILED' }, '돕는 사람 코드에 권한 없음');
    eq(bad.fieldErrors.map((f) => `${f.field}:${f.code}`), ['permission:REQUIRED'], '권한 필수');
    const active = await owner.api.pairing.join(code, 'VIEW');
    eq([active.status, active.permission, active.issuedBy, active.partnerName], ['ACTIVE', 'VIEW', 'HELPER', '아들'], '바로 연결');

    // 같은 사이 다시: 이미 연결
    const again = (await helper.api.pairing.issueCode('HELPER')).code;
    await expectApiError(owner.api.pairing.join(again, 'EDIT'), { status: 409, code: 'PAIRING_EXISTS' }, '이미 연결됨');
    // 내 코드는 내가 입력할 수 없다
    await expectApiError(helper.api.pairing.join(again, 'EDIT'), { status: 400, code: 'PAIRING_SELF' }, '자기 코드');

    // 주인은 돕는 사람 1명까지 (남은 한 칸 없음)
    const other = await signedUp('pair2-x', '삼촌');
    const full = await expectApiError(
      owner.api.pairing.join((await other.api.pairing.issueCode('HELPER')).code, 'EDIT'),
      { status: 409, code: 'PAIRING_LIMIT' },
      '돕는 사람 인원 초과',
    );
    eq([full.params?.side, full.params?.max], ['OWNER', 1], '인원 초과 params');

    await owner.api.pairing.end(active.id);

    // 거절: 돕는 사람이 주인 코드를 넣고 주인이 거절
    const pending = await helper.api.pairing.join((await owner.api.pairing.issueCode('OWNER')).code);
    await owner.api.pairing.reject(pending.id);
    eq((await helper.api.pairing.list()).asHelper.length, 0, '거절 뒤 목록');
    await expectApiError(owner.api.pairing.reject(pending.id), { status: 404, code: 'NOT_FOUND' }, '끝난 요청 거절');

    // 없는 코드
    await expectApiError(owner.api.pairing.join('ZZZZ-ZZZZ'), { status: 404, code: 'PAIRING_CODE_INVALID' }, '없는 코드');
    await expectApiError(owner.api.pairing.issueCode('FRIEND' as never), { status: 400, code: 'VALIDATION_FAILED' }, '모르는 자리');
  },
};

export const pairingScenarios: Scenario[] = [ownerCodeFlow, helperCodeFlow];
