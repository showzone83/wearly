// 옷 속성 코드 조회 (doc/06_코드_관리.md 3장·5장).
// 서버가 내려준 코드(GET /codes)를 색인해 두고 라벨·속성을 읽는다. 저장·갱신은 src/store/useCodes.ts가 한다.
// 추천 엔진이 Node(검증 스크립트)에서도 돌 수 있도록 React Native·Expo를 import하지 않는다.
import type { CodeDto, CodeGroupKey, CodesResponse } from '@/api/types';
import { SLOT_LABEL } from '@/data/constants';
import type { Category, ColorKey, ItemStatus, Pattern, Season, Slot, StyleTag, SubType } from '@/data/types';

export type { CodeGroupKey };

export interface Code {
  code: string;
  label: string;
  parent: string | null;
  sortOrder: number;
  active: boolean;
  attrs: Record<string, unknown>;
}

export interface SubTypeMeta {
  /** CATEGORY 코드 */
  parent: string | null;
  /** 실루엣 볼륨: -1 = 슬림, 0 = 레귤러, 1 = 볼륨 */
  volume: -1 | 0 | 1;
  /** 보온 정도 0~3 */
  warmth: number;
  /** 일러스트 모양 키 (src/data/garmentShapes.ts) */
  shape: string | null;
}

export interface ColorMeta {
  hex: string;
  /** 무채색·뉴트럴 계열 (조합 부담이 적다) */
  neutral: boolean;
  /** 밝기 0(어두움)~1(밝음) */
  light: number;
  /** 잘 어울리는 색 코드 */
  pairs: string[];
}

const SLOT_VALUES: Slot[] = ['TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'];

/** 모르는 색(앱 캐시보다 새 코드)에 쓰는 중간 회색 */
const UNKNOWN_COLOR: ColorMeta = { hex: '#B5B7BB', neutral: true, light: 0.6, pairs: [] };
const UNKNOWN_SUBTYPE: SubTypeMeta = { parent: null, volume: 0, warmth: 1, shape: null };

interface CodeIndex {
  version: string | null;
  groups: Map<string, Code[]>;
  byKey: Map<string, Code>;
  subTypes: Map<string, SubTypeMeta>;
  colors: Map<string, ColorMeta>;
}

function emptyIndex(): CodeIndex {
  return { version: null, groups: new Map(), byKey: new Map(), subTypes: new Map(), colors: new Map() };
}

let index: CodeIndex = emptyIndex();

const key = (group: string, code: string) => `${group}\u0000${code}`;

function num(v: unknown, fallback: number): number {
  return typeof v === 'number' && Number.isFinite(v) ? v : fallback;
}

/**
 * 서버 응답이나 앱에 넣어 둔 초기값(doc/06_코드_초기값.json 사본)을 같은 모양으로 맞춘다.
 * 초기값 JSON에는 active가 없고 version이 숫자다.
 */
export function normalizeCodes(raw: unknown): CodesResponse {
  const r = (raw ?? {}) as { version?: unknown; groups?: unknown };
  const groups = Array.isArray(r.groups) ? r.groups : [];
  return {
    version: String(r.version ?? ''),
    groups: groups.map((g: { group?: unknown; name?: unknown; codes?: unknown }) => ({
      group: String(g.group ?? ''),
      name: String(g.name ?? ''),
      codes: (Array.isArray(g.codes) ? g.codes : []).map((c: Partial<CodeDto>, i: number) => ({
        code: String(c.code ?? ''),
        label: String(c.label ?? c.code ?? ''),
        parent: c.parent ?? null,
        sortOrder: num(c.sortOrder, i),
        active: c.active !== false,
        attrs: c.attrs && typeof c.attrs === 'object' ? (c.attrs as Record<string, unknown>) : {},
      })),
    })),
  };
}

function buildIndex(res: CodesResponse): CodeIndex {
  const idx = emptyIndex();
  idx.version = res.version;
  for (const g of res.groups) {
    const codes = [...g.codes].sort((a, b) => a.sortOrder - b.sortOrder);
    idx.groups.set(g.group, codes);
    for (const c of codes) idx.byKey.set(key(g.group, c.code), c);
  }
  for (const c of idx.groups.get('SUB_TYPE') ?? []) {
    const v = num(c.attrs.volume, 0);
    idx.subTypes.set(c.code, {
      parent: c.parent,
      volume: v < 0 ? -1 : v > 0 ? 1 : 0,
      warmth: num(c.attrs.warmth, 1),
      shape: typeof c.attrs.shape === 'string' ? c.attrs.shape : null,
    });
  }
  for (const c of idx.groups.get('COLOR') ?? []) {
    const hex = typeof c.attrs.hex === 'string' && /^#[0-9a-f]{6}$/i.test(c.attrs.hex) ? c.attrs.hex : UNKNOWN_COLOR.hex;
    idx.colors.set(c.code, {
      hex,
      neutral: c.attrs.neutral === true,
      light: Math.max(0, Math.min(1, num(c.attrs.light, 0.5))),
      pairs: Array.isArray(c.attrs.pairs) ? c.attrs.pairs.filter((x): x is string => typeof x === 'string') : [],
    });
  }
  return idx;
}

/** 조회에 쓸 코드를 바꾼다. null이면 비운다 */
export function setCodes(res: CodesResponse | null): void {
  index = res ? buildIndex(res) : emptyIndex();
}

export function hasCodes(): boolean {
  return index.groups.size > 0;
}

export function codesVersion(): string | null {
  return index.version;
}

// ── 목록 ──

/** 그룹의 모든 코드 (비활성 포함, 순서대로) */
export function allCodes(group: CodeGroupKey): Code[] {
  return index.groups.get(group) ?? [];
}

/** 새로 고를 수 있는 코드 (활성만, 순서대로) */
export function activeCodes(group: CodeGroupKey): string[] {
  return allCodes(group)
    .filter((c) => c.active)
    .map((c) => c.code);
}

/**
 * 고르는 목록 + 이미 저장된 값. 비활성이거나 앱이 모르는 값이 저장돼 있으면
 * 선택된 칩으로 보이도록 끝에 붙인다(새로 고를 수는 없게 다른 값을 누르면 빠진다).
 */
export function withCurrent(options: string[], current: string | string[] | undefined): string[] {
  const cur = Array.isArray(current) ? current : current ? [current] : [];
  const extra = cur.filter((c) => c && !options.includes(c));
  return extra.length ? [...options, ...extra] : options;
}

export function codeOf(group: CodeGroupKey, code: string): Code | undefined {
  return index.byKey.get(key(group, code));
}

export function isKnownCode(group: CodeGroupKey, code: string): boolean {
  return index.byKey.has(key(group, code));
}

export function isActiveCode(group: CodeGroupKey, code: string): boolean {
  return index.byKey.get(key(group, code))?.active === true;
}

// ── 라벨 ──

/** 표시 이름. 모르는 코드는 코드 문자열 그대로 */
export function codeLabel(group: CodeGroupKey, code: string): string {
  return index.byKey.get(key(group, code))?.label ?? code;
}

export const categoryLabel = (c: Category) => codeLabel('CATEGORY', c);
export const subTypeLabel = (c: SubType) => codeLabel('SUB_TYPE', c);
export const colorLabel = (c: ColorKey) => codeLabel('COLOR', c);
export const patternLabel = (c: Pattern) => codeLabel('PATTERN', c);
export const seasonLabel = (c: Season) => codeLabel('SEASON', c);
export const styleLabel = (c: StyleTag) => codeLabel('STYLE', c);
export const statusLabel = (c: ItemStatus) => codeLabel('ITEM_STATUS', c);

// ── 속성 ──

export function subTypeMeta(code: SubType): SubTypeMeta {
  return index.subTypes.get(code) ?? UNKNOWN_SUBTYPE;
}

export function colorMeta(code: ColorKey): ColorMeta {
  return index.colors.get(code) ?? UNKNOWN_COLOR;
}

/** 두 색이 잘 어울리는지. pairs는 양방향으로 적게 돼 있지만 한쪽에만 있어도 인정한다 */
export function isGoodPair(a: ColorKey, b: ColorKey): boolean {
  return colorMeta(a).pairs.includes(b) || colorMeta(b).pairs.includes(a);
}

/** 종류 → 코디 칸. 모르는 종류이거나 slot이 null(액세서리 등)이면 코디에 넣지 않는다 */
export function categorySlot(category: Category): Slot | null {
  const slot = codeOf('CATEGORY', category)?.attrs.slot;
  return typeof slot === 'string' && (SLOT_VALUES as string[]).includes(slot) ? (slot as Slot) : null;
}

/** 코디 칸 이름: 그 칸의 첫 활성 종류 라벨, 없으면 앱 기본 이름 */
export function slotLabel(slot: Slot): string {
  const cat = allCodes('CATEGORY').find((c) => c.active && c.attrs.slot === slot);
  return cat?.label ?? SLOT_LABEL[slot];
}

/** 추천 후보에 넣는 보유 상태인지 (세탁·수선 중이면 false). 모르는 상태는 넣지 않는다 */
export function isRecommendable(status: ItemStatus): boolean {
  return codeOf('ITEM_STATUS', status)?.attrs.recommendable === true;
}

/** 새 아이템의 기본 보유 상태: 추천에 들어가는 첫 활성 상태 */
export function defaultStatus(): ItemStatus {
  return allCodes('ITEM_STATUS').find((c) => c.active && c.attrs.recommendable === true)?.code ?? 'OWNED';
}

/** 종류의 세부 종류 (활성만, 순서대로) */
export function subTypesOf(category: Category): SubType[] {
  return allCodes('SUB_TYPE')
    .filter((c) => c.active && c.parent === category)
    .map((c) => c.code);
}

/** 세부 종류의 상위 종류 */
export function parentOf(subType: SubType): Category | null {
  return codeOf('SUB_TYPE', subType)?.parent ?? null;
}
