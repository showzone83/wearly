// 로그인 세션 라벨 (doc/16). 서버 값 → 화면 문구. 모르는 값은 화면이 원문 그대로 보여 준다.

type TBadgeTone = 'green' | 'gray' | 'red' | 'beige' | 'ink';

/** 로그인한 곳 (X-Wearly-Client) */
export const SESSION_CLIENTS: Record<string, { icon: string; label: string; tone: TBadgeTone }> = {
  APP: { icon: '▯', label: '앱', tone: 'green' },
  WEB: { icon: '◫', label: '웹', tone: 'beige' },
  ADMIN: { icon: '◆', label: '관리자 사이트', tone: 'ink' },
};

/** 기기 필터 선택지 */
export const SESSION_CLIENT_OPTIONS = Object.entries(SESSION_CLIENTS).map(([value, c]) => ({
  value,
  label: c.label,
}));

/** 로그인 이력 종류 (tb_auth_event.type) */
export const AUTH_EVENTS: Record<string, { label: string; tone: TBadgeTone }> = {
  LOGIN: { label: '로그인', tone: 'green' },
  LOGOUT: { label: '로그아웃', tone: 'gray' },
  FORCED_LOGOUT: { label: '강제 로그아웃', tone: 'red' },
  REFRESH_REUSED: { label: '토큰 재사용 감지', tone: 'red' },
};

/** 세션을 확인 대화상자·토스트에서 부르는 이름: "앱 · iOS · Expo Go" */
export const sessionDeviceText = (client: string | null, uaLabel: string): string => {
  const c = client ? (SESSION_CLIENTS[client]?.label ?? client) : null;
  return [c, uaLabel === '—' ? null : uaLabel].filter(Boolean).join(' · ') || '알 수 없는 기기';
};
