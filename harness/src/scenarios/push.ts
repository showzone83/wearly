// 예정 D-1 알림 서버 푸시: PUT/DELETE /me/push-token.
// 서버 계약 자체를 확인하려고 앱 API 모듈(mobile/src/api의 api.push) 대신 원시 HTTP(raw)로 부른다.
// 하네스 서버는 서버 푸시가 꺼져 있다(기본값) → serverPush는 false (앱은 로컬 알림을 쓴다). D-1 발송은 백엔드 테스트가 지킨다.
import { randomUUID } from 'node:crypto';
import { eq } from '../lib/assert';
import { checkErrorBody, raw, type RawResponse } from '../lib/http';
import { rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const token = () => `ExponentPushToken[${randomUUID().replace(/-/g, '')}]`;

const fieldCodes = (res: RawResponse) =>
  (res.json.fieldErrors as { field: string; code: string }[]).map((e) => `${e.field}:${e.code}`);

const register: Scenario = {
  group: '푸시 토큰',
  name: '토큰 없이 401 → 등록 200(키 4개, serverPush false, dayBeforeTime) → 같은 기기 다시 등록 200 → 검증 400(token·platform·deviceId) → 삭제 204(없어도 204, deviceId 없으면 400)',
  async run() {
    checkErrorBody(await raw('PUT', '/me/push-token', { body: { token: token(), platform: 'ios', deviceId: 'h-dev' } }), 401, 'UNAUTHORIZED', '토큰 없이 등록');
    const user = await rawSignup('push');
    const first = await user.call('PUT', '/me/push-token', { body: { token: token(), platform: 'ios', deviceId: 'h-dev-1' } });
    eq(first.status, 200, '등록 200');
    eq(Object.keys(first.json).sort(), ['dayBeforeTime', 'deviceId', 'platform', 'serverPush'], '응답 키 4개');
    eq(first.json.deviceId, 'h-dev-1', 'deviceId');
    eq(first.json.platform, 'ios', 'platform 소문자');
    eq(first.json.serverPush, false, '서버 푸시 꺼짐(기본) → 앱은 로컬 알림');
    eq(/^\d{2}:\d{2}$/.test(first.json.dayBeforeTime), true, 'dayBeforeTime HH:mm');
    const again = await user.call('PUT', '/me/push-token', { body: { token: token(), platform: 'android', deviceId: 'h-dev-1' } });
    eq(again.status, 200, '같은 기기 다시 등록 200');
    eq(again.json.platform, 'android', '종류 바뀜');

    const bad = await user.call('PUT', '/me/push-token', { body: { token: 'raw-fcm-token', platform: 'web', deviceId: '기기 1' } });
    eq(bad.status, 400, '검증 400');
    eq(bad.json.code, 'VALIDATION_FAILED', 'VALIDATION_FAILED');
    eq(fieldCodes(bad), ['token:INVALID_FORMAT', 'platform:NOT_ALLOWED', 'deviceId:INVALID_FORMAT'], '필드 오류');
    eq(fieldCodes(await user.call('PUT', '/me/push-token', { body: {} })), ['token:REQUIRED', 'platform:REQUIRED', 'deviceId:REQUIRED'], '빈 본문');

    eq((await user.call('DELETE', '/me/push-token?deviceId=h-dev-1')).status, 204, '삭제 204');
    eq((await user.call('DELETE', '/me/push-token?deviceId=h-dev-1')).status, 204, '없어도 204');
    eq(fieldCodes(await user.call('DELETE', '/me/push-token')), ['deviceId:REQUIRED'], 'deviceId 없으면 400');
  },
};

export const pushScenarios: Scenario[] = [register];
