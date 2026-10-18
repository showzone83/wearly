// 앱에서 쓰는 백엔드 API 인스턴스: 서버 주소 결정 + Keychain/Keystore 토큰 저장소.
// 순수 TS 클라이언트(@/api)에 React Native·Expo 의존 부분을 여기서 끼워 넣는다.
import Constants from 'expo-constants';
import { File } from 'expo-file-system';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { createApiClient, isApiError, type AnalyzeFile, type ApiError, type Tokens, type TokenStore } from '@/api';
import { errorAlertText, errorText } from './errorText';

const API_PORT = 8080;

/** 개발 서버(Metro) 주소에서 Mac의 IP를 뽑는다. 예: "192.168.0.12:8081" → "192.168.0.12" */
function devServerHost(): string | null {
  const raw =
    Constants.expoConfig?.hostUri ??
    Constants.platform?.hostUri ??
    (Constants.expoGoConfig as { debuggerHost?: string } | null)?.debuggerHost;
  if (!raw) return null;
  const host = raw
    .replace(/^[a-z]+:\/\//i, '')
    .split('/')[0]
    .replace(/:\d+$/, '');
  // 터널 모드(*.exp.direct)는 Metro 포트만 중계하므로 백엔드 포트에는 닿지 않는다
  if (!host || host.endsWith('.exp.direct')) return null;
  return host;
}

function resolveBaseUrl(): string {
  // 1) 명시한 주소가 우선 (mobile/.env 의 EXPO_PUBLIC_API_URL)
  const env = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (env) return env.replace(/\/+$/, '');
  // 2) 웹 체험판은 같은 Mac의 백엔드
  if (Platform.OS === 'web') return `http://localhost:${API_PORT}`;
  // 3) 휴대폰·시뮬레이터: Expo 개발 서버를 띄운 Mac의 IP (같은 Wi-Fi)
  let host = devServerHost() ?? 'localhost';
  // Android 에뮬레이터에서 localhost는 에뮬레이터 자신이다. 호스트 Mac은 10.0.2.2
  if (Platform.OS === 'android' && (host === 'localhost' || host === '127.0.0.1')) host = '10.0.2.2';
  return `http://${host}:${API_PORT}`;
}

export const API_BASE_URL = resolveBaseUrl();

// ── 토큰 저장소 ──

const TOKEN_KEY = 'wearly.auth.tokens';
const SECURE_OPTS: SecureStore.SecureStoreOptions = { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK };

function parseTokens(raw: string | null): Tokens | null {
  if (!raw) return null;
  try {
    const t = JSON.parse(raw) as Partial<Tokens>;
    return typeof t.accessToken === 'string' && typeof t.refreshToken === 'string'
      ? { accessToken: t.accessToken, refreshToken: t.refreshToken }
      : null;
  } catch {
    return null;
  }
}

async function readRaw(): Promise<string | null> {
  if (Platform.OS === 'web') {
    // 웹에는 Keychain이 없다. 체험판 용도로만 브라우저 저장소를 쓴다
    try {
      return globalThis.localStorage?.getItem(TOKEN_KEY) ?? null;
    } catch {
      return null;
    }
  }
  try {
    return await SecureStore.getItemAsync(TOKEN_KEY, SECURE_OPTS);
  } catch (e) {
    console.warn('[api] 토큰을 읽지 못했어요', e);
    return null;
  }
}

async function writeRaw(value: string | null): Promise<void> {
  if (Platform.OS === 'web') {
    try {
      if (value === null) globalThis.localStorage?.removeItem(TOKEN_KEY);
      else globalThis.localStorage?.setItem(TOKEN_KEY, value);
    } catch {
      // 사생활 보호 모드 등: 이번 실행 동안은 메모리 캐시로 동작한다
    }
    return;
  }
  try {
    if (value === null) await SecureStore.deleteItemAsync(TOKEN_KEY, SECURE_OPTS);
    else await SecureStore.setItemAsync(TOKEN_KEY, value, SECURE_OPTS);
  } catch (e) {
    console.warn('[api] 토큰을 저장하지 못했어요', e);
  }
}

/** Keychain(iOS)·Keystore(Android)에 토큰을 두고, 매 요청마다 읽지 않도록 메모리에 캐시한다 */
let cache: Tokens | null | undefined;
export const tokenStore: TokenStore = {
  async get() {
    if (cache === undefined) cache = parseTokens(await readRaw());
    return cache ? { ...cache } : null;
  },
  async set(t) {
    cache = { accessToken: t.accessToken, refreshToken: t.refreshToken };
    await writeRaw(JSON.stringify(cache));
  },
  async clear() {
    cache = null;
    await writeRaw(null);
  },
};

// ── 클라이언트 ──

let authLostHandler: ((reason: ApiError) => void) | null = null;

/** 로그인이 풀렸을 때(Refresh 실패·토큰 무효) 할 일을 등록한다. src/lib/account.ts가 등록한다 */
export function setAuthLostHandler(fn: (reason: ApiError) => void) {
  authLostHandler = fn;
}

export const api = createApiClient({
  baseUrl: API_BASE_URL,
  tokenStore,
  onAuthLost: (reason) => authLostHandler?.(reason),
});

// ── 화면용 도우미 ──

export function isNetworkError(e: unknown): boolean {
  return isApiError(e) && e.code === 'NETWORK';
}

/** 사용자에게 보여 줄 오류 문구 (코드별 문구 표: src/lib/errorText.ts). 오류 번호는 넣지 않는다 */
export function errorMessage(e: unknown, fallback = '잠시 후 다시 시도해 주세요.'): string {
  return errorText(e, { fallback, baseUrl: API_BASE_URL });
}

/** 알림(notify) 본문용 오류 문구: 시스템 오류면 끝에 "오류 번호 ab12cd34" 한 줄 */
export function errorAlertMessage(e: unknown, fallback = '잠시 후 다시 시도해 주세요.'): string {
  return errorAlertText(e, { fallback, baseUrl: API_BASE_URL });
}

/**
 * 이미지 선택 결과를 분석 요청용 파일로 바꾼다. 웹은 Blob, 네이티브는 expo-file-system의 File.
 * Expo SDK 57의 전역 fetch(expo/fetch)는 RN 방식 { uri, name, type } 파트를 보내지 못하고
 * 'Unsupported FormDataPart implementation'으로 실패한다. File은 Blob 인터페이스를 구현해 바이트를 읽어 보낸다.
 */
export async function photoFile(asset: { uri: string }): Promise<AnalyzeFile> {
  if (Platform.OS === 'web') return (await fetch(asset.uri)).blob();
  return new File(asset.uri);
}
