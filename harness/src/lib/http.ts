// 원시 HTTP 도구. 앱 모듈을 거치지 않고 서버 응답(상태·오류 본문 형식)을 직접 확인할 때 쓴다.
import { apiBase, config } from './config';
import { eq, fail, ok, isNonEmptyString } from './assert';


export interface RawResponse {
  status: number;
  headers: Headers;
  text: string;
  /** JSON이 아니면 undefined */
  json: any;
}

export interface RawOptions {
  token?: string;
  body?: unknown;
  headers?: Record<string, string>;
}

/** 앱 모듈을 거치지 않고 직접 부른다. path가 /로 시작하면 /api/v1 뒤에 붙인다. */
export async function raw(method: string, path: string, opts: RawOptions = {}): Promise<RawResponse> {
  const url = path.startsWith('http') ? path : path.startsWith('/actuator') ? config.origin + path : apiBase() + path;
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(url, { method, headers, body });
  const text = await res.text();
  let json: any;
  try {
    json = text ? JSON.parse(text) : undefined;
  } catch {
    json = undefined;
  }
  return { status: res.status, headers: res.headers, text, json };
}

/** 계약 4장 오류 응답 형식 검사: { status, code, message, fieldErrors[] } */
export function checkErrorBody(res: RawResponse, status: number, code: string | readonly string[], label: string): void {
  const codes = typeof code === 'string' ? [code] : code;
  if (res.status !== status || !codes.includes(res.json?.code)) {
    fail(`${label}: HTTP 상태·오류 코드`, `${status} ${codes.join(' 또는 ')}`, `${res.status} ${res.json?.code ?? res.text.slice(0, 300)}`);
  }
  const ct = res.headers.get('content-type') ?? '';
  ok(ct.includes('application/json'), `${label}: 오류 응답 Content-Type이 JSON`, ct);
  const b = res.json;
  eq(b.status, status, `${label}: 본문 status`);
  ok(isNonEmptyString(b.message), `${label}: 본문 message는 비어 있지 않은 문자열`, b.message);
  ok(Array.isArray(b.fieldErrors), `${label}: 본문 fieldErrors는 배열`, b.fieldErrors);
}
