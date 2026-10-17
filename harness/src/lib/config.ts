// 실행 옵션. main.ts가 명령줄 인자를 읽어 한 번 채운다.

export interface HarnessConfig {
  /** 백엔드 주소 (예: http://localhost:18080). 끝에 / 없음 */
  origin: string;
  /** --external: 하네스가 띄운 백엔드가 아니라서 TTL·AI 한도 설정을 알 수 없다 */
  external: boolean;
}

export const config: HarnessConfig = {
  origin: 'http://localhost:18080',
  external: false,
};

/** REST 기본 경로 (계약 4장) */
export function apiBase(): string {
  return `${config.origin}/api/v1`;
}

/** run.sh가 백엔드를 띄울 때 넣는 값 (계약 9장). --external이면 알 수 없다. */
export const HARNESS_ACCESS_TTL_SEC = 3;
export const HARNESS_AI_DAILY_LIMIT = 3;

/** Access 토큰이 확실히 만료될 때까지 기다리는 시간 (TTL 3초 + 여유) */
export const WAIT_FOR_EXPIRY_MS = 4_500;

/** run.sh가 백엔드를 띄울 때 만드는 관리자 계정 (07 문서 2장). --external이면 쓸 수 없다. */
export const HARNESS_ADMIN_EMAIL = 'admin@harness.wearly.test';
export const HARNESS_ADMIN_PASSWORD = 'HarnessAdmin1234';
