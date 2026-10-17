export interface Tokens {
  accessToken: string;
  refreshToken: string;
}

/** 토큰 보관소. 앱은 Keychain/Keystore(expo-secure-store), 하네스는 메모리를 쓴다. */
export interface TokenStore {
  get(): Promise<Tokens | null>;
  set(t: Tokens): Promise<void>;
  clear(): Promise<void>;
}

export function createMemoryTokenStore(initial: Tokens | null = null): TokenStore {
  let tokens: Tokens | null = initial ? { ...initial } : null;
  return {
    async get() {
      return tokens ? { ...tokens } : null;
    },
    async set(t) {
      tokens = { accessToken: t.accessToken, refreshToken: t.refreshToken };
    },
    async clear() {
      tokens = null;
    },
  };
}
