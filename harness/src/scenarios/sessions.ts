// 로그인 세션·강제 로그아웃 (doc/16): 관리자 세션 목록, 세션 하나 끊기, 회원 전체 로그아웃, 로그아웃 즉시 401
// 앱 API 모듈을 거치지 않고 원시 HTTP(raw)와 src/lib/rawUser.ts로만 확인한다.
// 하네스 서버는 Access 수명이 3초라서, "끊은 뒤 다음 요청이 401"은 끊기 직전에 새로 로그인한 토큰으로 확인한다
// (만료가 먼저 오면 TOKEN_EXPIRED가 되기 때문).
import { eq, isIsoDateTime, ok } from '../lib/assert';
import { HARNESS_ADMIN_EMAIL } from '../lib/config';
import { checkErrorBody, raw } from '../lib/http';
import { rawAdmin, rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

/** rawUser.ts와 같은 비밀번호 */
const PASSWORD = 'Harness1234';

interface Tokens {
  accessToken: string;
  refreshToken: string;
  sid: string;
}

/** Access 토큰(JWT)의 sid 클레임 */
function sidOf(accessToken: string): string {
  const payload = JSON.parse(Buffer.from(accessToken.split('.')[1], 'base64url').toString('utf8'));
  return payload.sid;
}

async function login(email: string, headers: Record<string, string> = {}): Promise<Tokens> {
  const res = await raw('POST', '/auth/login', { body: { email, password: PASSWORD }, headers });
  eq(res.status, 200, `로그인 200 (${email})`);
  return { accessToken: res.json.accessToken, refreshToken: res.json.refreshToken, sid: sidOf(res.json.accessToken) };
}

const SESSION_KEYS = [
  'client',
  'createdAt',
  'current',
  'email',
  'ip',
  'lastRefreshAt',
  'lastSeenAt',
  'name',
  'role',
  'sessionId',
  'userAgent',
  'userId',
];

const listed: Scenario = {
  group: '로그인 세션',
  name: '관리자 세션 목록에 로그인 세션이 보인다: 항목 모양, 기기 종류·IP, 최신순, 기기 필터, 내 세션 current, 대시보드 숫자',
  needs: ['admin'],
  async run(ctx) {
    const u = await rawSignup('sess-list');
    const web = await login(u.email, { 'X-Wearly-Client': 'web', 'X-Forwarded-For': '198.51.100.7, 10.0.0.1', 'User-Agent': 'Mozilla/5.0 Harness' });
    const admin = await rawAdmin();

    const res = await admin.call('GET', `/admin/sessions?q=${encodeURIComponent(u.email)}&size=10`);
    eq(res.status, 200, '세션 목록 200');
    ok(['page', 'size', 'total'].every((k) => Number.isInteger(res.json[k])), '목록 페이지 형식 { items, page, size, total }', res.json);
    eq(res.json.total, 2, '가입 + 로그인 = 세션 2개');
    const [first, second] = res.json.items;
    eq(Object.keys(first).sort(), SESSION_KEYS, '항목 키');
    eq(first.sessionId, web.sid, '최신순: 마지막 로그인이 먼저');
    eq([first.client, first.ip, first.userAgent], ['WEB', '198.51.100.7', 'Mozilla/5.0 Harness'], 'X-Wearly-Client·X-Forwarded-For 첫 값·User-Agent');
    eq([first.userId, first.email, first.role, first.current], [u.id, u.email, 'USER', false], '회원 정보와 current');
    ok(isIsoDateTime(first.createdAt) && isIsoDateTime(first.lastSeenAt) && first.lastRefreshAt === null, '시각 형식, 갱신 전 lastRefreshAt null', first);
    eq(second.client, 'APP', '헤더 없이 가입한 Node fetch → APP');

    const filtered = await admin.call('GET', `/admin/sessions?q=${encodeURIComponent(u.email)}&client=WEB`);
    eq(filtered.json.items.map((x: { sessionId: string }) => x.sessionId), [web.sid], '기기 필터 client=WEB');
    checkErrorBody(await admin.call('GET', '/admin/sessions?client=TV'), 400, 'VALIDATION_FAILED', '모르는 client');

    const mine = await admin.call('GET', `/admin/sessions?q=${encodeURIComponent(HARNESS_ADMIN_EMAIL)}&size=100`);
    const current = mine.json.items.filter((x: { current: boolean }) => x.current).map((x: { sessionId: string }) => x.sessionId);
    eq(current, [sidOf(admin.token())], '내 현재 세션만 current');

    const detail = await admin.call('GET', `/admin/users/${u.id}/sessions`);
    eq(detail.status, 200, '회원 세션 200');
    eq(detail.json.sessions.length, 2, '회원 세션 2개');
    eq(detail.json.events.map((e: { type: string }) => e.type), ['LOGIN', 'LOGIN'], '최근 로그인 이력 (최신순)');
    // ssoProvider·ssoAmr은 회사 계정(SSO) 로그인·거부에만 값이 있다 (doc/22 4.2, 비밀번호 로그인은 null)
    eq(
      Object.keys(detail.json.events[0]).sort(),
      ['client', 'createdAt', 'ip', 'sessionId', 'ssoAmr', 'ssoProvider', 'type', 'userAgent'],
      '이력 항목 키',
    );
    eq([detail.json.events[0].ssoProvider, detail.json.events[0].ssoAmr], [null, null], '비밀번호 로그인은 SSO 값 없음');

    const dash = await admin.call('GET', '/admin/dashboard');
    ok(Number.isInteger(dash.json.activeSessions) && dash.json.activeSessions >= 3, '대시보드 activeSessions', dash.json.activeSessions);
    ok(Number.isInteger(dash.json.activeUsers24h) && dash.json.activeUsers24h >= 2, '대시보드 activeUsers24h', dash.json.activeUsers24h);
    ctx.note(`activeSessions = ${dash.json.activeSessions}, activeUsers24h = ${dash.json.activeUsers24h}`);
  },
};

const revokeOne: Scenario = {
  group: '로그인 세션',
  name: '강제 로그아웃(세션 하나): 그 기기의 다음 요청 401 TOKEN_INVALID, refresh도 REFRESH_INVALID, 다른 기기는 그대로, 내 세션 400, 이력',
  needs: ['admin'],
  async run() {
    const u = await rawSignup('sess-one');
    const admin = await rawAdmin();
    const other = await login(u.email, { 'X-Wearly-Client': 'web' });
    const phone = await login(u.email, { 'X-Wearly-Client': 'app' });

    eq((await admin.call('DELETE', `/admin/sessions/${phone.sid}`)).status, 204, '세션 끊기 204');
    checkErrorBody(await raw('GET', '/users/me', { token: phone.accessToken }), 401, 'TOKEN_INVALID', '끊긴 세션의 다음 요청');
    checkErrorBody(await raw('POST', '/auth/refresh', { body: { refreshToken: phone.refreshToken } }), 401, 'REFRESH_INVALID', '끊긴 세션의 refresh');
    eq((await raw('POST', '/auth/refresh', { body: { refreshToken: other.refreshToken } })).status, 200, '다른 기기의 refresh는 그대로');

    checkErrorBody(await admin.call('DELETE', `/admin/sessions/${phone.sid}`), 404, 'NOT_FOUND', '이미 끊긴 세션');
    // Access 수명 3초: 토큰이 만료돼 다시 로그인하면 sid가 바뀌므로 방금 로그인한 관리자로
    const fresh = await rawAdmin();
    const self = await raw('DELETE', `/admin/sessions/${sidOf(fresh.token())}`, { token: fresh.token() });
    checkErrorBody(self, 400, 'VALIDATION_FAILED', '내 현재 세션 끊기');
    eq(self.json.fieldErrors.map((f: { field: string }) => f.field), ['sessionId'], '400 필드');

    const audit = await admin.call('GET', '/admin/audit-logs?targetType=SESSION&size=20');
    const row = audit.json.items.find((a: { targetId: string }) => a.targetId === phone.sid);
    ok(row && row.action === 'SESSION_REVOKE' && row.before.email === u.email, '변경 이력 SESSION_REVOKE (targetType SESSION)', row);
    const events = (await admin.call('GET', `/admin/users/${u.id}/sessions`)).json.events.map((e: { type: string }) => e.type);
    eq(events[0], 'FORCED_LOGOUT', '로그인 이력 맨 위 FORCED_LOGOUT');
  },
};

const revokeAll: Scenario = {
  group: '로그인 세션',
  name: '회원 전체 로그아웃: 모든 기기 401·refresh 실패, 다시 로그인은 됨, 자기 자신 400, 없는 회원 404, 이력 USER_SESSIONS_REVOKE',
  needs: ['admin'],
  async run() {
    const u = await rawSignup('sess-all');
    const admin = await rawAdmin();
    const a = await login(u.email, { 'X-Wearly-Client': 'app' });
    const b = await login(u.email, { 'X-Wearly-Client': 'web' });

    eq((await admin.call('DELETE', `/admin/users/${u.id}/sessions`)).status, 204, '회원 전체 끊기 204');
    for (const [label, t] of [['기기 A', a], ['기기 B', b]] as const) {
      checkErrorBody(await raw('GET', '/users/me', { token: t.accessToken }), 401, 'TOKEN_INVALID', `${label} 다음 요청`);
      checkErrorBody(await raw('POST', '/auth/refresh', { body: { refreshToken: t.refreshToken } }), 401, 'REFRESH_INVALID', `${label} refresh`);
    }
    const list = await admin.call('GET', `/admin/sessions?q=${encodeURIComponent(u.email)}`);
    eq(list.json.total, 0, '목록에서 사라짐');

    const again = await login(u.email);
    eq((await raw('GET', '/users/me', { token: again.accessToken })).status, 200, '다시 로그인하면 된다');

    checkErrorBody(await admin.call('DELETE', `/admin/users/${admin.id}/sessions`), 400, 'VALIDATION_FAILED', '자기 자신 전체 끊기');
    checkErrorBody(await admin.call('DELETE', '/admin/users/999999999/sessions'), 404, 'NOT_FOUND', '없는 회원');

    const audit = await admin.call('GET', '/admin/audit-logs?targetType=USER&size=20');
    const row = audit.json.items.find((x: { action: string; targetId: string }) => x.action === 'USER_SESSIONS_REVOKE' && x.targetId === u.id);
    ok(row && row.before.sessionCount === 3, '변경 이력 USER_SESSIONS_REVOKE (가입·로그인 2 = 세션 3개)', row);
  },
};

const logoutNow: Scenario = {
  group: '로그인 세션',
  name: '로그아웃하면 그 세션의 Access 토큰도 수명과 상관없이 바로 401 TOKEN_INVALID, 다른 세션은 그대로, 일반 회원은 세션 API 403',
  async run() {
    const u = await rawSignup('sess-out');
    const t = await login(u.email);
    const other = await login(u.email);
    eq((await raw('GET', '/users/me', { token: t.accessToken })).status, 200, '로그아웃 전 200');
    eq((await raw('POST', '/auth/logout', { body: { refreshToken: t.refreshToken } })).status, 204, '로그아웃 204');
    checkErrorBody(await raw('GET', '/users/me', { token: t.accessToken }), 401, 'TOKEN_INVALID', '로그아웃한 세션의 Access');
    eq((await raw('GET', '/users/me', { token: other.accessToken })).status, 200, '다른 세션은 그대로');
    checkErrorBody(await raw('GET', '/admin/sessions', { token: other.accessToken }), 403, 'FORBIDDEN', '일반 회원의 세션 목록');
  },
};

export const sessionScenarios: Scenario[] = [listed, revokeOne, revokeAll, logoutNow];
