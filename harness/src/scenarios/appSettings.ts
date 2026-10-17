// 앱 설정·디자인 테마 (doc/12 3장): 공개 GET /app/config, ETag 304, 관리자 GET·PUT /admin/app/theme, 변경 이력
// 앱 API 모듈을 거치지 않고 원시 HTTP(raw)로만 확인한다.
import { eq, ok } from '../lib/assert';
import { checkErrorBody, raw } from '../lib/http';
import { rawAdmin, rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const THEMES = ['nyang', 'closeta'];

const publicConfig: Scenario = {
  group: '앱 설정',
  name: 'GET /app/config: 토큰 없이(틀린 토큰도) 200, { themeId, version }, ETag·Cache-Control no-cache, If-None-Match → 304',
  async run(ctx) {
    const res = await raw('GET', '/app/config');
    eq(res.status, 200, '토큰 없이 200');
    eq(Object.keys(res.json).sort(), ['themeId', 'version'], '응답 키는 themeId·version뿐');
    ok(THEMES.includes(res.json.themeId), 'themeId는 nyang 또는 closeta', res.json.themeId);
    ok(typeof res.json.version === 'string' && res.json.version.length > 0, 'version은 비어 있지 않은 문자열', res.json.version);
    const etag = res.headers.get('etag');
    eq(etag, `"${res.json.version}"`, 'ETag = "version"');
    eq(res.headers.get('cache-control'), 'no-cache', 'Cache-Control');

    const again = await raw('GET', '/app/config', { headers: { 'If-None-Match': etag! } });
    eq(again.status, 304, '같은 ETag → 304');
    eq(again.text, '', '304 본문 없음');

    const other = await raw('GET', '/app/config', { headers: { 'If-None-Match': '"other"' } });
    eq(other.status, 200, '다른 ETag → 200');
    eq((await raw('GET', '/app/config', { token: 'not-a-jwt' })).status, 200, '틀린 토큰이 같이 와도 200 (공개 경로는 토큰을 보지 않음)');
    checkErrorBody(await raw('PUT', '/app/config', { body: { themeId: 'closeta' } }), 401, 'UNAUTHORIZED', '공개는 GET만');

    const cors = await raw('GET', '/app/config', { headers: { Origin: 'http://localhost:8081' } });
    ok((cors.headers.get('access-control-expose-headers') ?? '').includes('ETag'), 'CORS: ETag 노출', cors.headers.get('access-control-expose-headers'));
    ctx.note(`테마 = ${res.json.themeId}, version = ${res.json.version}`);
  },
};

const adminTheme: Scenario = {
  group: '앱 설정',
  name: '관리자 테마: GET 형식, PUT으로 바꾸면 /app/config version·ETag 변경, 같은 값은 그대로, 400·403, 변경 이력 APP_THEME_UPDATE',
  needs: ['admin'],
  async run(ctx) {
    const admin = await rawAdmin();
    const user = await rawSignup('theme-user');
    checkErrorBody(await user.call('GET', '/admin/app/theme'), 403, 'FORBIDDEN', '일반 회원 GET');
    checkErrorBody(await user.call('PUT', '/admin/app/theme', { body: { themeId: 'closeta' } }), 403, 'FORBIDDEN', '일반 회원 PUT');
    checkErrorBody(await raw('GET', '/admin/app/theme'), 401, 'UNAUTHORIZED', '토큰 없이');

    const before = await admin.call('GET', '/admin/app/theme');
    eq(before.status, 200, '관리자 GET 200');
    eq(Object.keys(before.json).sort(), ['themeId', 'updatedAt', 'updatedBy'], '응답 키');
    if (before.json.updatedAt === null) {
      eq(before.json.updatedBy, null, '바꾼 적이 없으면 updatedBy도 null');
      ctx.note('처음 상태: updatedAt·updatedBy = null');
    }
    const from: string = before.json.themeId;
    const to = from === 'nyang' ? 'closeta' : 'nyang';
    const cfg0 = await raw('GET', '/app/config');
    eq(cfg0.json.themeId, from, '/app/config와 관리자 GET의 themeId가 같음');
    const etag0 = cfg0.headers.get('etag')!;

    for (const [body, label] of [
      [{ themeId: 'dark' }, '허용하지 않는 테마'],
      [{}, 'themeId 없음'],
      [{ themeId: 'NYANG' }, '대소문자 다름'],
    ] as const) {
      const bad = await admin.call('PUT', '/admin/app/theme', { body });
      checkErrorBody(bad, 400, 'VALIDATION_FAILED', label);
      eq(bad.json.fieldErrors.map((e: { field: string }) => e.field), ['themeId'], `${label}: fieldErrors`);
    }

    const changed = await admin.call('PUT', '/admin/app/theme', { body: { themeId: to } });
    eq(changed.status, 200, `PUT ${to} 200`);
    eq(changed.json.themeId, to, 'PUT 응답 themeId');
    eq(changed.json.updatedBy, admin.email, 'updatedBy = 관리자 이메일');
    ok(typeof changed.json.updatedAt === 'string' && !Number.isNaN(Date.parse(changed.json.updatedAt)), 'updatedAt은 ISO 시각', changed.json.updatedAt);

    const cfg1 = await raw('GET', '/app/config', { headers: { 'If-None-Match': etag0 } });
    eq(cfg1.status, 200, '바뀐 뒤 옛 ETag로 부르면 200 (304 아님)');
    eq(cfg1.json.themeId, to, '/app/config에 새 테마');
    ok(cfg1.json.version !== cfg0.json.version, 'version이 바뀜', [cfg0.json.version, cfg1.json.version]);
    eq(cfg1.json.version, changed.json.updatedAt, 'version = 바꾼 시각(updatedAt)');
    eq((await raw('GET', '/app/config', { headers: { 'If-None-Match': cfg1.headers.get('etag')! } })).status, 304, '새 ETag → 304');

    const countBefore = (await admin.call('GET', '/admin/audit-logs?targetType=APP_SETTING')).json.total;
    const same = await admin.call('PUT', '/admin/app/theme', { body: { themeId: to } });
    eq(same.json, changed.json, '같은 테마로 다시 PUT하면 그대로 (시각·관리자 안 바뀜)');
    eq((await admin.call('GET', '/admin/audit-logs?targetType=APP_SETTING')).json.total, countBefore, '같은 값 PUT은 이력을 남기지 않음');

    const audit = await admin.call('GET', '/admin/audit-logs?targetType=APP_SETTING&size=50');
    eq(audit.status, 200, '변경 이력 200');
    const mine = audit.json.items.filter(
      (a: { action: string; after: { themeId?: string } | null; before: { themeId?: string } | null }) =>
        a.action === 'APP_THEME_UPDATE' && a.before?.themeId === from && a.after?.themeId === to,
    );
    ok(mine.length >= 1, `변경 이력: APP_THEME_UPDATE ${from} → ${to}`, audit.json.items);
    const log = mine[0];
    eq([log.targetType, log.targetId, log.adminEmail], ['APP_SETTING', 'theme', admin.email], '이력 대상·관리자');
    eq(log.before, { themeId: from }, '이력 before');
    eq(log.after, { themeId: to }, '이력 after');

    // 원래대로 되돌린다 (다른 실행에 영향을 주지 않게)
    const back = await admin.call('PUT', '/admin/app/theme', { body: { themeId: from } });
    eq(back.json.themeId, from, `원래 테마(${from})로 되돌림`);
    eq((await raw('GET', '/app/config')).json.themeId, from, '/app/config도 원래 테마');
  },
};

export const appSettingScenarios: Scenario[] = [publicConfig, adminTheme];
