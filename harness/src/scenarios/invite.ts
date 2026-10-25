// 친구 초대 (doc/25): 앱 API 모듈 api.invite·auth.signup(inviteCode)과 관리자 이력(원시 HTTP).
import { eq, expectApiError, isIsoDateTime, ok } from '../lib/assert';
import { rawAdmin } from '../lib/rawUser';
import { newSession, randomEmail, signedUp, TEST_PASSWORD } from '../lib/session';
import type { Scenario } from '../scenario';

const CODE_RE = /^[A-HJKMNP-Z2-9]{8}$/;

const inviteFlow: Scenario = {
  group: '친구 초대',
  name: '코드 발급(다시 열어도 같음) → 공유 기록 204·잘못된 채널 400 → 소문자·하이픈 코드로 가입 → 친구 1명·관리자 가입 이력 LINKED·회원 목록 추천인',
  async run() {
    const a = await signedUp('inv-a', '초대한사람');
    const first = await a.api.invite.get();
    ok(CODE_RE.test(first.inviteCode), '초대 코드 8자 형식', first.inviteCode);
    eq(first.invitedCount, 0, '처음 초대 수');
    eq((await a.api.invite.get()).inviteCode, first.inviteCode, '다시 열어도 같은 코드');

    await a.api.invite.recordShare('SHARE');
    await a.api.invite.recordShare('COPY');
    const bad = await expectApiError(
      a.api.invite.recordShare('KAKAO' as never),
      { status: 400, code: 'VALIDATION_FAILED' },
      '모르는 채널',
    );
    eq(bad.fieldErrors.map((f) => `${f.field}:${f.code}`), ['channel:NOT_ALLOWED'], '채널 필드 오류');

    const code = first.inviteCode;
    const b = newSession();
    const typed = `${code.slice(0, 4)}-${code.slice(4)}`.toLowerCase();
    const res = await b.api.auth.signup({ email: randomEmail('inv-b'), password: TEST_PASSWORD, name: '초대받은사람', inviteCode: typed });

    const after = await a.api.invite.get();
    eq(after.invitedCount, 1, '초대 수 1');
    const friends = await a.api.invite.friends();
    eq(friends.map((f) => f.name), ['초대받은사람'], '초대로 가입한 친구');
    ok(isIsoDateTime(friends[0].joinedAt), 'joinedAt ISO', friends[0].joinedAt);

    const admin = await rawAdmin();
    const signups = await admin.call('GET', `/admin/invites/signups?q=${code}`);
    eq(signups.status, 200, '관리자 가입 이력 200');
    const row = signups.json.items.find((r: { userId: string }) => r.userId === res.user.id);
    ok(row, '가입 이력에 B', signups.json.items);
    eq([row.enteredCode, row.result, row.referrerId], [code, 'LINKED', (await a.api.users.me()).id], '입력 코드·결과·추천인');

    const shares = await admin.call('GET', `/admin/invites/shares?q=${encodeURIComponent(a.email)}`);
    eq(shares.json.items.map((r: { channel: string }) => r.channel), ['COPY', 'SHARE'], '초대한 이력 최신순');

    const users = await admin.call('GET', `/admin/users?q=${encodeURIComponent(res.user.email ?? '')}`);
    eq(users.json.items[0].referrerName, '초대한사람', '회원 목록 추천인 이름');
  },
};

const unknownCode: Scenario = {
  group: '친구 초대',
  name: '없는 코드로 가입해도 201 → 가입 이력 UNKNOWN_CODE·추천인 없음, 코드 없이 가입하면 이력 없음',
  async run() {
    const s = newSession();
    const res = await s.api.auth.signup({ email: randomEmail('inv-x'), password: TEST_PASSWORD, name: '모르는코드', inviteCode: 'zzzz-zzz9' });
    ok(res.user.id, '가입 성공', res.user);

    const admin = await rawAdmin();
    const list = await admin.call('GET', '/admin/invites/signups?result=UNKNOWN_CODE&size=100');
    const row = list.json.items.find((r: { userId: string }) => r.userId === res.user.id);
    ok(row, 'UNKNOWN_CODE 이력에 있음', list.json.items.length);
    eq([row.enteredCode, row.referrerId], ['ZZZZZZZ9', null], '정리한 입력 코드·추천인 없음');

    const plain = await signedUp('inv-none');
    const me = await plain.api.users.me();
    const all = await admin.call('GET', `/admin/invites/signups?q=${encodeURIComponent(plain.email)}`);
    eq(all.json.total, 0, `코드 없이 가입한 ${me.id}는 이력 없음`);
  },
};

export const inviteScenarios: Scenario[] = [inviteFlow, unknownCode];
