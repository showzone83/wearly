// 시나리오용 검사 함수. 실패하면 기대값과 실제값을 담은 CheckFailure를 던진다.
import { isDeepStrictEqual, inspect } from 'node:util';
import { ApiError } from '../../../mobile/src/api/errors';

export class CheckFailure extends Error {
  constructor(
    message: string,
    /** 기대값·실제값. 없으면 메시지만 보여 준다 */
    readonly values?: { expected: unknown; actual: unknown },
  ) {
    super(message);
    this.name = 'CheckFailure';
  }
}

export function fail(label: string, ...values: [] | [expected: unknown, actual: unknown]): never {
  if (values.length === 2) throw new CheckFailure(label, { expected: values[0], actual: values[1] });
  throw new CheckFailure(label);
}

/** 깊은 비교 (객체 키 순서 무관, 배열 순서는 따진다) */
export function eq<T>(actual: unknown, expected: T, label: string): asserts actual is T {
  if (!isDeepStrictEqual(actual, expected)) fail(label, expected, actual);
}

export function ok(cond: unknown, label: string, actual?: unknown): asserts cond {
  if (!cond) {
    if (arguments.length > 2) fail(label, '(조건 충족)', actual);
    fail(label);
  }
}

/** 순서 무관 집합 비교 */
export function sameSet(actual: readonly unknown[], expected: readonly unknown[], label: string): void {
  const a = [...actual].map(String).sort();
  const e = [...expected].map(String).sort();
  if (!isDeepStrictEqual(a, e)) fail(label, e, a);
}

export function isNonEmptyString(v: unknown): v is string {
  return typeof v === 'string' && v.length > 0;
}

export function isIsoDateTime(v: unknown): boolean {
  return typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(v) && !Number.isNaN(Date.parse(v));
}

export interface ExpectedApiError {
  status: number;
  /** 하나 또는 허용되는 여러 코드 */
  code: string | readonly string[];
}

/**
 * promise가 앱 모듈의 ApiError로 실패하는지 확인하고 그 오류를 돌려준다.
 * 성공하거나 다른 종류의 오류면 실패 처리.
 */
export async function expectApiError(
  promise: Promise<unknown>,
  expected: ExpectedApiError,
  label: string,
): Promise<ApiError> {
  let value: unknown;
  try {
    value = await promise;
  } catch (err) {
    if (!(err instanceof ApiError)) {
      fail(`${label}: ApiError가 아닌 오류`, `ApiError(${expected.status} ${codes(expected.code)})`, describeError(err));
    }
    const codeOk = Array.isArray(expected.code) ? expected.code.includes(err.code) : err.code === expected.code;
    if (err.status !== expected.status || !codeOk) {
      fail(label, `${expected.status} ${codes(expected.code)}`, `${err.status} ${err.code} (${err.message})`);
    }
    return err;
  }
  return fail(`${label}: 오류가 나야 하는데 성공함`, `ApiError(${expected.status} ${codes(expected.code)})`, value);
}

function codes(c: string | readonly string[]): string {
  return Array.isArray(c) ? c.join(' 또는 ') : String(c);
}

export function describeError(err: unknown): string {
  if (err instanceof ApiError) return `ApiError ${err.status} ${err.code}: ${err.message}`;
  if (err instanceof AggregateError && err.errors.length) {
    return err.errors.map(describeError).join(', ');
  }
  if (err instanceof Error) {
    const { cause, code } = err as { cause?: unknown; code?: unknown };
    const head = `${err.name}${typeof code === 'string' ? ` ${code}` : ''}: ${err.message}`;
    return `${head}${cause ? ` (cause: ${describeError(cause)})` : ''}`;
  }
  return inspect(err, { depth: 4 });
}

/** 실패 출력용 값 표시 (너무 길면 자른다) */
export function show(v: unknown): string {
  const s = typeof v === 'string' ? v : inspect(v, { depth: 6, breakLength: 100, compact: 3 });
  return s.length > 1500 ? `${s.slice(0, 1500)} …(생략)` : s;
}
