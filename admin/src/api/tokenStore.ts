// 토큰과 로그인한 관리자 정보는 sessionStorage에 둔다 (doc/07 5장). 탭을 닫으면 사라진다.
import type { IUserDto } from './types';

export interface ITokens {
  accessToken: string;
  refreshToken: string;
}

const TOKENS_KEY = 'wearly-admin-tokens';
const USER_KEY = 'wearly-admin-user';

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
  },
  getUser: (): IUserDto | null => {
    return read<IUserDto>(USER_KEY);
  },
  setUser: (user: IUserDto): void => {
    write(USER_KEY, user);
  },
};
