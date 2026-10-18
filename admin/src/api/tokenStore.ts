// 토큰과 로그인한 관리자 정보는 sessionStorage에 둔다 (doc/07 5장). 탭을 닫으면 사라진다.
import type { IUserDto } from '@/api/types';

export interface ITokens {
  accessToken: string;
  refreshToken: string;
}

const TOKENS_KEY = 'wearly-admin-tokens';
const USER_KEY = 'wearly-admin-user';
/** 회사 계정(SSO)으로 로그인한 공급자 ID. 로그아웃할 때 IdP 로그아웃으로도 보낸다 (doc/22 4.2) */
const SSO_KEY = 'wearly-admin-sso';
/** SSO 시작 ~ 콜백 사이에 들고 있는 공급자 ID (IdP로 갔다 오는 동안 페이지가 새로 열린다) */
const SSO_PENDING_KEY = 'wearly-admin-sso-pending';

const read = <T>(key: string): T | null => {
  try {
    const raw = sessionStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
};

const write = (key: string, value: unknown): void => {
  try {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 이번 탭 메모리에서만 유지된다
  }
};

let memoryTokens: ITokens | null = read<ITokens>(TOKENS_KEY);

export const tokenStore = {
  get: (): ITokens | null => {
    return memoryTokens;
  },
  set: (tokens: ITokens): void => {
    memoryTokens = tokens;
    write(TOKENS_KEY, tokens);
  },
  clear: (): void => {
    memoryTokens = null;
    write(TOKENS_KEY, null);
    write(USER_KEY, null);
    write(SSO_KEY, null);
  },
  getUser: (): IUserDto | null => {
    return read<IUserDto>(USER_KEY);
  },
  setUser: (user: IUserDto): void => {
    write(USER_KEY, user);
  },
  getSsoProvider: (): string | null => {
    return read<string>(SSO_KEY);
  },
  setSsoProvider: (providerId: string | null): void => {
    write(SSO_KEY, providerId);
  },
  /** SSO 시작 때 공급자를 적어 두고, 콜백에서 꺼낸다(한 번만) */
  setSsoPending: (providerId: string | null): void => {
    write(SSO_PENDING_KEY, providerId);
  },
  takeSsoPending: (): string | null => {
    const v = read<string>(SSO_PENDING_KEY);
    write(SSO_PENDING_KEY, null);
    return v;
  },
};
