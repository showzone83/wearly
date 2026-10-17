// 앱 코드 초기값(mobile/src/data/codes.seed.json = doc/06_코드_초기값.json 사본)으로 예전 앱 상수(SUBTYPE·COLOR)와
// 같은 모양의 표를 만든다. 옷 속성 값이 앱 상수에서 서버 코드로 옮겨 가(doc/06) 시나리오가 쓰던 import를 대신한다.
import seed from '../../../mobile/src/data/codes.seed.json';

interface SeedCode {
  code: string;
  parent: string | null;
  attrs: Record<string, unknown>;
}

function group(name: string): SeedCode[] {
  return (seed.groups.find((g) => g.group === name)?.codes ?? []) as SeedCode[];
}

/** 세부 종류 코드 → { category: 상위 종류 코드 } */
export const SUBTYPE: Record<string, { category: string }> = Object.fromEntries(
  group('SUB_TYPE').map((c) => [c.code, { category: c.parent ?? '' }]),
);

/** 색 코드 → attrs (hex·neutral·light·pairs) */
export const COLOR: Record<string, Record<string, unknown>> = Object.fromEntries(group('COLOR').map((c) => [c.code, c.attrs]));
