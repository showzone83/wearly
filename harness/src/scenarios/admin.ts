// 관리자 API (doc/07): 권한, 코드 관리, AI 공급자, 회원 정지
import { eq, expectApiError, ok } from '../lib/assert';
import { HARNESS_ADMIN_EMAIL, HARNESS_ADMIN_PASSWORD } from '../lib/config';
import { item } from '../lib/fixtures';
import { checkErrorBody, raw } from '../lib/http';
import { loadCodes } from '../lib/serverCodes';
import { newSession, signedUp, tokensOf, type UserSession } from '../lib/session';
import type { Scenario } from '../scenario';

/** 관리자로 로그인하고 Access 토큰을 돌려준다 */
async function adminToken(): Promise<string> {
  const s = newSession();
  const res = await s.api.auth.login({ email: HARNESS_ADMIN_EMAIL, password: HARNESS_ADMIN_PASSWORD });
  eq((res.user as { role?: string }).role, 'ADMIN', '관리자 로그인 응답 user.role');
  return (await tokensOf(s)).accessToken;
}

async function userToken(u: UserSession): Promise<string> {
  return (await tokensOf(u)).accessToken;
}

const access: Scenario = {
  group: '관리자',
  name: '권한: 일반 회원 403 FORBIDDEN, 관리자 대시보드·목록 형식',
  needs: ['admin'],
  async run(ctx) {
    const u = await signedUp('adm-user');
    checkErrorBody(await raw('GET', '/admin/dashboard', { token: await userToken(u) }), 403, 'FORBIDDEN', '일반 회원의 관리자 API');
    checkErrorBody(await raw('GET', '/admin/dashboard'), 401, 'UNAUTHORIZED', '토큰 없이 관리자 API');

    const token = await adminToken();
    const dash = await raw('GET', '/admin/dashboard', { token });
    eq(dash.status, 200, '대시보드 200');
    ok(Number.isInteger(dash.json.users) && dash.json.aiToday && Array.isArray(dash.json.aiByProvider7d), '대시보드 형식', dash.json);
    const users = await raw('GET', `/admin/users?q=${encodeURIComponent(u.email)}`, { token });
    eq(users.status, 200, '회원 목록 200');
    eq(users.json.items.map((x: { email: string }) => x.email), [u.email], '회원 검색');
    ok(['page', 'size', 'total'].every((k) => Number.isInteger(users.json[k])), '목록 페이지 형식', users.json);
    ctx.note(`대시보드 회원 수 = ${dash.json.users}`);
  },
};

const codeAdmin: Scenario = {
  group: '관리자',
  name: '코드: 추가 → /codes 반영 → 비활성(새로 고르기 400, 저장값 유지) → 사용 중 삭제 409 → 삭제',
  needs: ['admin'],
  async run() {
    const token = await adminToken();
    const before = await loadCodes(true);
    const code = `H${Date.now().toString(36).toUpperCase()}`.slice(0, 12);

    const created = await raw('POST', '/admin/codes/PATTERN', { token, body: { code, label: '하네스 무늬', attrs: {} } });
    eq(created.status, 201, '코드 추가 201');
    checkErrorBody(await raw('POST', '/admin/codes/PATTERN', { token, body: { code, label: '중복', attrs: {} } }), 409, 'CODE_EXISTS', '같은 코드 다시 추가');
    checkErrorBody(
      await raw('POST', '/admin/codes/COLOR', { token, body: { code: 'harness_bad', label: '잘못된 색', attrs: { hex: 'blue', neutral: false, light: 0.5 } } }),
      400,
      'VALIDATION_FAILED',
      '색 hex 형식 오류',
    );

    const changed = await newSession().api.codes.list(before.etag);
    ok(changed.changed && changed.codes.groups.find((g) => g.group === 'PATTERN')!.codes.some((c) => c.code === code), '코드가 바뀌면 /codes ETag가 바뀌고 새 코드가 보임');

    const u = await signedUp('adm-code');
    const it = await u.api.wardrobe.create(item({ pattern: code }));
    eq(it.pattern, code, '새 패턴으로 아이템 등록');

    eq((await raw('PATCH', `/admin/codes/PATTERN/${code}`, { token, body: { active: false } })).status, 200, '비활성화');
    await expectApiError(u.api.wardrobe.create(item({ pattern: code })), { status: 400, code: 'VALIDATION_FAILED' }, '비활성 코드로 새 아이템');
    const patched = await u.api.wardrobe.update(it.id, { name: '이름만 바꿈', pattern: code });
    eq([patched.name, patched.pattern], ['이름만 바꿈', code], 'PATCH: 바꾸지 않은 비활성 값은 그대로 통과');

    checkErrorBody(await raw('DELETE', `/admin/codes/PATTERN/${code}`, { token }), 409, 'CODE_IN_USE', '사용 중인 코드 삭제');
    await u.api.wardrobe.remove(it.id);
    eq((await raw('DELETE', `/admin/codes/PATTERN/${code}`, { token })).status, 204, '쓰는 아이템이 없으면 삭제 204');

    const audit = await raw('GET', '/admin/audit-logs?targetType=CODE&size=20', { token });
    const actions = audit.json.items.filter((a: { targetId: string }) => a.targetId === `PATTERN:${code}`).map((a: { action: string }) => a.action);
    ok(['CODE_CREATE', 'CODE_UPDATE', 'CODE_DELETE'].every((x) => actions.includes(x)), '변경 이력: 추가·수정·삭제', actions);
    await loadCodes(true);
  },
};

const providers: Scenario = {
  group: '관리자',
  name: 'AI 공급자: 목록(keyConfigured), 종류 설명, 추가·중복 409·삭제',
  needs: ['admin'],
  async run() {
    const token = await adminToken();
    const list = await raw('GET', '/admin/ai/providers', { token });
    eq(list.status, 200, '공급자 목록 200');
    const claude = list.json.find((p: { providerId: string }) => p.providerId === 'claude');
    ok(claude && claude.type === 'CLAUDE' && typeof claude.keyConfigured === 'boolean', 'claude 행', claude);
    ok(!JSON.stringify(list.json).includes('sk-ant-'), '응답에 키 문자열 없음');

    const types = await raw('GET', '/admin/ai/types', { token });
    const typeNames = (types.json.types as { type: string }[]).map((t) => t.type);
    ok(['CLAUDE', 'OPENAI', 'GEMINI'].every((t) => typeNames.includes(t)), '지원 종류', typeNames);

    const id = `h-openai-${Date.now().toString(36)}`;
    const body = { providerId: id, type: 'OPENAI', displayName: '하네스 OpenAI', apiKeyEnv: 'OPENAI_API_KEY', enabled: false, priority: 50 };
    eq((await raw('POST', '/admin/ai/providers', { token, body })).status, 201, '공급자 추가 201');
    checkErrorBody(await raw('POST', '/admin/ai/providers', { token, body }), 409, 'PROVIDER_EXISTS', '같은 ID 다시 추가');
    checkErrorBody(
      await raw('POST', '/admin/ai/providers', { token, body: { ...body, providerId: `${id}-x`, apiKeyEnv: 'WEARLY_JWT_SECRET' } }),
      400,
      'VALIDATION_FAILED',
      '키 환경변수로 서버 비밀 이름 거부',
    );
    checkErrorBody(await raw('PATCH', `/admin/ai/providers/${id}`, { token, body: { enabled: true } }), 400, 'VALIDATION_FAILED', '모델 없이 사용 켜기');
    eq((await raw('DELETE', `/admin/ai/providers/${id}`, { token })).status, 204, '공급자 삭제 204');
  },
};

const providerModels: Scenario = {
  group: '관리자',
  name: 'AI 공급자 모델 목록: 키 없는 공급자는 내장 목록(CATALOG + NO_API_KEY), 권한 403·없는 공급자 404',
  needs: ['admin'],
  async run(ctx) {
    const token = await adminToken();
    // 하네스는 실제 키를 쓰지 않는다: 값이 없는 키 이름으로 공급자를 만든다 (제공사를 부르지 않는다)
    const id = `h-models-${Date.now().toString(36)}`;
    const body = { providerId: id, type: 'CLAUDE', displayName: '하네스 모델 목록', apiKeyEnv: 'HARNESS_MISSING_API_KEY', enabled: false, priority: 60 };
    eq((await raw('POST', '/admin/ai/providers', { token, body })).status, 201, '공급자 추가 201');
    try {
      for (const query of ['', '?refresh=true']) {
        const res = await raw('GET', `/admin/ai/providers/${id}/models${query}`, { token });
        eq(res.status, 200, `모델 목록 200${query}`);
        eq(res.json.source, 'CATALOG', `키가 없으면 내장 목록${query}`);
        eq(res.json.error?.code, 'NO_API_KEY', `이유 NO_API_KEY${query}`);
        ok(typeof res.json.error?.message === 'string' && res.json.error.message.length > 0, '이유 문구', res.json.error);
        ok(typeof res.json.fetchedAt === 'string' && res.json.fetchedAt.endsWith('Z'), 'fetchedAt ISO 시각', res.json.fetchedAt);
        const models = res.json.models as { id: string; displayName: string; createdAt: string | null; recommended: boolean }[];
        ok(Array.isArray(models) && models.length > 0, '내장 목록이 비어 있지 않음', models);
        eq(models[0].recommended, true, '추천 모델이 맨 앞');
        ok(models.every((m) => typeof m.id === 'string' && typeof m.displayName === 'string' && m.createdAt === null), '항목 형식(내장 목록은 createdAt null)', models);
        ok(models.some((m) => m.id === 'claude-sonnet-5-5' && m.recommended), 'claude-sonnet-5-5 추천', models);
      }
      const types = await raw('GET', '/admin/ai/types', { token });
      const claudeType = (types.json.types as { type: string; models?: { id: string }[] }[]).find((t) => t.type === 'CLAUDE');
      ok(Array.isArray(claudeType?.models) && claudeType!.models!.some((m) => m.id === 'claude-sonnet-5-5'), '종류 목록에도 내장 모델', claudeType);

      const u = await signedUp('adm-models');
      checkErrorBody(await raw('GET', `/admin/ai/providers/${id}/models`, { token: await userToken(u) }), 403, 'FORBIDDEN', '일반 회원의 모델 목록');
      checkErrorBody(await raw('GET', '/admin/ai/providers/h-nope-models/models', { token }), 404, 'NOT_FOUND', '없는 공급자 모델 목록');
      ctx.note('내장 목록만 확인 (실제 제공사 호출 없음)');
    } finally {
      eq((await raw('DELETE', `/admin/ai/providers/${id}`, { token })).status, 204, '공급자 삭제 204');
    }
  },
};

const suspend: Scenario = {
  group: '관리자',
  name: '회원 정지: 로그인 403 ACCOUNT_SUSPENDED, 기존 토큰 401 → 해제 후 로그인',
  needs: ['admin'],
  async run() {
    const token = await adminToken();
    const u = await signedUp('adm-susp');
    const access = await userToken(u);
    const me = await u.api.users.me();

    eq((await raw('PATCH', `/admin/users/${me.id}`, { token, body: { status: 'SUSPENDED' } })).status, 200, '정지');
    await expectApiError(newSession().api.auth.login({ email: u.email, password: u.password }), { status: 403, code: 'ACCOUNT_SUSPENDED' }, '정지 회원 로그인');
    checkErrorBody(await raw('GET', '/users/me', { token: access }), 401, 'TOKEN_INVALID', '정지 회원의 기존 토큰');

    eq((await raw('PATCH', `/admin/users/${me.id}`, { token, body: { status: 'ACTIVE' } })).status, 200, '정지 해제');
    const again = newSession();
    await again.api.auth.login({ email: u.email, password: u.password });
    eq((await again.api.users.me()).id, me.id, '해제 후 로그인');
  },
};

export const adminScenarios: Scenario[] = [access, codeAdmin, providers, providerModels, suspend];
