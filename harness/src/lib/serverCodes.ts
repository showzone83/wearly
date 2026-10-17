// 서버가 내려주는 코드(GET /codes)를 한 번 받아 둔다.
// 허용 값·짝 검사를 하드코딩 목록이 아니라 서버 코드 기준으로 하기 위해 쓴다.
import type { CodeDto, CodesResponse } from '../../../mobile/src/api/types';
import { newSession } from './session';

export interface ServerCodes {
  raw: CodesResponse;
  etag: string;
  /** 그룹의 고를 수 있는(활성) 코드 */
  active(group: string): CodeDto[];
  /** 그룹의 활성 코드 문자열 */
  activeSet(group: string): Set<string>;
  /** SUB_TYPE 코드의 상위 CATEGORY */
  parentOf(subType: string): string | null;
}

let cached: Promise<ServerCodes> | null = null;

export async function loadCodes(force = false): Promise<ServerCodes> {
  if (!cached || force) cached = fetchCodes();
  return cached;
}

async function fetchCodes(): Promise<ServerCodes> {
  const res = await newSession().api.codes.list();
  if (!res.changed) throw new Error('ETag 없이 요청했는데 304가 왔다');
  const groups = new Map(res.codes.groups.map((g) => [g.group, g.codes]));
  const active = (group: string) => (groups.get(group) ?? []).filter((c) => c.active);
  return {
    raw: res.codes,
    etag: res.etag,
    active,
    activeSet: (group) => new Set(active(group).map((c) => c.code)),
    parentOf: (subType) => groups.get('SUB_TYPE')?.find((c) => c.code === subType)?.parent ?? null,
  };
}
