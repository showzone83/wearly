// 관리자 사이트 회사 계정 로그인 SSO (doc/22 4.2). 하네스 서버는 SSO를 끈다(run.sh WEARLY_SSO_ENABLED=false, 기본값과 같다).
// 꺼진 기본 동작만 본다: 공급자 목록은 비고, 시작·콜백·교환·로그아웃은 404 NOT_FOUND. 켠 흐름은 백엔드 SsoIntegrationTest(가짜 IdP)와
// 로컬 Keycloak(docker compose --profile sso up -d keycloak)으로 확인한다.
import { eq, ok } from '../lib/assert';
import { checkErrorBody, raw } from '../lib/http';
import type { Scenario } from '../scenario';

const SITE_CALLBACK = 'http://localhost:5173/sso/callback';

const disabledByDefault: Scenario = {
  group: '관리자',
  name: 'SSO 꺼짐(기본): 공급자 목록은 비고 시작·콜백·교환·로그아웃은 404',
  async run(ctx) {
    const list = await raw('GET', '/auth/sso/providers');
    eq(list.status, 200, '공급자 목록 200 (로그인 없이)');
    ok(typeof list.json?.enabled === 'boolean' && Array.isArray(list.json?.providers), '{ enabled, providers[] } 모양', list.json);
    if (ctx.external && list.json.enabled) {
      ctx.note(`외부 서버는 SSO가 켜져 있음: 공급자 ${list.json.providers.map((p: { id: string }) => p.id).join(', ')}`);
      return;
    }
    eq([list.json.enabled, list.json.providers.length], [false, 0], '꺼짐: enabled false · 빈 목록');

    const redirect = encodeURIComponent(SITE_CALLBACK);
    checkErrorBody(await raw('GET', `/auth/sso/keycloak/start?redirect=${redirect}`), 404, 'NOT_FOUND', '시작');
    checkErrorBody(await raw('GET', '/auth/sso/keycloak/callback?code=x&state=y'), 404, 'NOT_FOUND', '콜백');
    checkErrorBody(await raw('GET', `/auth/sso/keycloak/logout?redirect=${redirect}`), 404, 'NOT_FOUND', '로그아웃');
    checkErrorBody(await raw('POST', '/auth/sso/exchange', { body: { code: 'x' } }), 404, 'NOT_FOUND', '교환');
  },
};

export const ssoScenarios: Scenario[] = [disabledByDefault];
