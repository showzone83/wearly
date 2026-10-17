// 규칙 기반 코디 추천 엔진 (기획서 p.24 초기 점수 모델)
// 조건 입력 → 옷장 조회 → 후보 생성 → 점수 평가 → 상위 선정 → 설명 생성
// 코디는 '상의+하의' 또는 '원피스' 하나로 몸판을 채운다(doc/06 5장). 옷 속성은 서버 코드(src/lib/codes.ts)에서 읽는다.
import { FORMALITY_LABEL, FORMALITY_LEVEL, OCCASION_FORMALITY, OCCASION_LABEL } from '@/data/constants';
import type {
  Category,
  ColorKey,
  Outfit,
  OutfitItems,
  Profile,
  RecommendContext,
  ScoreBreakdown,
  Season,
  Slot,
  StyleTag,
  WardrobeItem,
  Weather,
} from '@/data/types';
import { categorySlot, colorLabel, colorMeta, isGoodPair, isRecommendable, subTypeMeta } from './codes';
import { diffDays } from './date';
import { josa } from './josa';
import { isRetired } from './retire';

export const WEIGHTS = { color: 0.3, occasion: 0.25, weather: 0.2, taste: 0.15, diversity: 0.1 };

export interface EngineInput {
  items: WardrobeItem[];
  ctx: RecommendContext;
  profile: Profile;
  /** 아이템별 선호도 (좋아요·교체·만족도 기록으로 누적) */
  affinity: Record<string, number>;
  /** 최근 저장한 코디 (같은 조합 반복 방지) */
  recentOutfits: OutfitItems[];
}

export type Resolved = Partial<Record<Slot, WardrobeItem>>;

const REQUIRED: Slot[] = ['TOP', 'BOTTOM'];
/** 몸에 걸치는 옷 칸 (최근 착용 제외·반복 판단 대상) */
const GARMENT_SLOTS: Slot[] = ['TOP', 'BOTTOM', 'DRESS', 'OUTER'];
/** 코디 id·차이 비교에 쓰는 칸 순서 */
const ID_SLOTS: Slot[] = ['TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'];

/** 종류 → 코디 칸 (CATEGORY attrs.slot). null이면 코디에 넣지 않는다 */
export function slotOf(category: Category): Slot | null {
  return categorySlot(category);
}

/** 추천 후보가 될 수 있는 옷: 사용 중(doc/13)이고 보유 상태가 추천 대상(세탁·수선 중이 아님) */
export function isCandidate(i: WardrobeItem): boolean {
  return !isRetired(i) && isRecommendable(i.status);
}

/** 상의+하의 또는 원피스가 있어야 코디를 만들 수 있다 */
export function canRecommend(items: WardrobeItem[]): { ok: boolean; missing: string[] } {
  const owned = items.filter(isCandidate);
  const has = (slot: Slot) => owned.some((i) => slotOf(i.category) === slot);
  if (has('DRESS')) return { ok: true, missing: [] };
  const missing: string[] = [];
  if (!has('TOP')) missing.push('상의');
  if (!has('BOTTOM')) missing.push('하의');
  return { ok: missing.length === 0, missing };
}

function clamp(n: number, lo = 0, hi = 1) {
  return Math.max(lo, Math.min(hi, n));
}

export function seasonsFor(temp: number): Season[] {
  if (temp >= 24) return ['SUMMER'];
  if (temp >= 17) return ['SPRING', 'FALL', 'SUMMER'];
  if (temp >= 9) return ['SPRING', 'FALL'];
  if (temp >= 4) return ['FALL', 'WINTER'];
  return ['WINTER'];
}

export function wantsOuter(w: Weather): boolean {
  return w.temp < 17 || (w.max - w.min >= 10 && w.min < 16);
}

/** 몸에 걸치는 옷: 상의·하의(또는 원피스)·아우터 */
function coreItems(r: Resolved): WardrobeItem[] {
  return [r.TOP, r.BOTTOM, r.DRESS, r.OUTER].filter(Boolean) as WardrobeItem[];
}

/**
 * 색 조화를 볼 옷. 원피스는 한 벌이라 아우터가 없으면 신발까지 넣어 상하의 두 벌과 같은 무게로 본다.
 * 위·아래 짝(밝기 대비·같은 색 판단)은 상의·하의, 원피스면 원피스·(아우터 또는 신발).
 */
function colorParts(r: Resolved): { parts: WardrobeItem[]; upper?: WardrobeItem; lower?: WardrobeItem } {
  if (r.DRESS && !r.TOP && !r.BOTTOM) {
    const partner = r.OUTER ?? r.SHOES;
    return { parts: [r.DRESS, partner].filter(Boolean) as WardrobeItem[], upper: r.DRESS, lower: partner };
  }
  return { parts: coreItems(r), upper: r.TOP, lower: r.BOTTOM };
}

// ---------------------------------------------------------------- 점수 항목

export function colorScore(r: Resolved): { score: number; reason?: string } {
  const { parts, upper, lower } = colorParts(r);
  let s = 0.6;
  let reason: string | undefined;

  const distinct: ColorKey[] = [...new Set(parts.map((i) => i.color))];
  for (let a = 0; a < distinct.length; a++) {
    for (let b = a + 1; b < distinct.length; b++) {
      if (isGoodPair(distinct[a], distinct[b])) {
        s += 0.15;
        reason ??= `${colorLabel(distinct[a])}×${colorLabel(distinct[b])} 조합이 안정적이에요`;
      }
    }
  }

  const accents = distinct.filter((c) => !colorMeta(c).neutral);
  if (accents.length >= 2) s -= 0.25;
  if (accents.length === 1 && distinct.length >= 2) {
    s += 0.05;
    reason ??= `${josa(colorLabel(accents[0]), '을', '를')} 포인트 컬러로 하나만 살렸어요`;
  }
  if (accents.length === 0 && distinct.length >= 2) {
    s += 0.1;
    reason ??= '뉴트럴 톤온톤으로 차분하게 맞췄어요';
  }

  if (upper && lower) {
    if (upper.color === lower.color && !colorMeta(upper.color).neutral) s -= 0.15;
    if (Math.abs(colorMeta(upper.color).light - colorMeta(lower.color).light) >= 0.3) s += 0.1;
  }

  const patterned = coreItems(r).filter((i) => i.pattern !== 'SOLID').length;
  if (patterned >= 2) s -= 0.2;

  if (r.SHOES && r.BAG) {
    const sameFamily =
      r.SHOES.color === r.BAG.color ||
      (colorMeta(r.SHOES.color).light < 0.35 && colorMeta(r.BAG.color).light < 0.35);
    if (sameFamily) s += 0.05;
  }

  return { score: clamp(s), reason };
}

/** 분위기별로 맞는 STYLE 코드 */
const MOOD_STYLES: Record<RecommendContext['mood'], StyleTag[]> = {
  NEAT: ['NEAT', 'CLASSIC', 'MINIMAL'],
  COMFY: ['CASUAL', 'SPORTY'],
  CHIC: ['MINIMAL', 'CLASSIC', 'FEMININE'],
};

export function occasionScore(r: Resolved, ctx: RecommendContext): { score: number; reason?: string } {
  const target = (OCCASION_FORMALITY[ctx.occasion] + FORMALITY_LEVEL[ctx.formality]) / 2;
  const parts = [r.TOP, r.BOTTOM, r.DRESS, r.OUTER, r.SHOES].filter(Boolean) as WardrobeItem[];
  if (parts.length === 0) return { score: 0 };
  // 덜 갖춰 입은 쪽(예: 회의에 스니커즈)을 더 갖춰 입은 쪽보다 크게 감점한다
  const itemFit = (f: number) => Math.max(0, 1 - (f < target ? (target - f) * 0.8 : (f - target) * 0.4));
  const fit = parts.reduce((sum, i) => sum + itemFit(i.formality), 0) / parts.length;

  const wanted = MOOD_STYLES[ctx.mood];
  const moodHits = parts.filter((i) => i.styles.some((s) => wanted.includes(s))).length / parts.length;
  let s = fit * 0.8 + moodHits * 0.2;
  if (ctx.mood === 'COMFY' && parts.some((i) => i.subType === 'SNEAKER' || i.subType === 'KNIT')) s += 0.05;

  const reason = `${OCCASION_LABEL[ctx.occasion]} — ${FORMALITY_LABEL[ctx.formality]}에 맞춘 격식이에요`;
  return { score: clamp(s), reason };
}

export function weatherScore(r: Resolved, w: Weather): { score: number; reason?: string } {
  let s = 0.7;
  let reason: string | undefined;
  const hasOuter = !!r.OUTER;
  const range = w.max - w.min;

  if (wantsOuter(w)) {
    if (hasOuter) {
      s += 0.2;
      reason =
        range >= 10
          ? `일교차 ${range}°C — ${josa(r.OUTER!.name, '을', '를')} 더했어요`
          : `최저 ${w.min}°C — ${josa(r.OUTER!.name, '으로', '로')} 보온을 챙겼어요`;
    } else {
      s -= 0.4;
    }
  } else if (w.temp >= 24 && hasOuter) {
    s -= 0.4;
  } else if (!hasOuter && w.temp >= 20) {
    reason = `낮 기온 ${w.max}°C — 아우터 없이 가볍게 입어요`;
  }

  const needed = w.temp < 5 ? 5 : w.temp < 10 ? 4 : w.temp < 15 ? 3 : w.temp < 20 ? 2 : 1;
  const warmth = coreItems(r).reduce((sum, i) => sum + subTypeMeta(i.subType).warmth, 0);
  s -= 0.08 * Math.abs(warmth - needed);

  const season = seasonsFor(w.temp);
  for (const i of coreItems(r)) s += i.seasons.some((x) => season.includes(x)) ? 0.03 : -0.1;

  if (w.rainProb >= 60 && r.SHOES) {
    if (colorMeta(r.SHOES.color).light > 0.7) s -= 0.15;
    else reason ??= '비 예보 — 젖어도 티가 덜 나는 어두운 신발로 골랐어요';
  }
  return { score: clamp(s), reason };
}

export function tasteScore(r: Resolved, input: EngineInput): number {
  const parts = Object.values(r).filter(Boolean) as WardrobeItem[];
  if (parts.length === 0) return 0;
  const liked: StyleTag[] = [...input.profile.moods, ...(input.ctx.concepts ?? [])];
  let s = 0;
  for (const i of parts) {
    let p = 0.5;
    if (liked.length > 0) p += 0.3 * (i.styles.filter((x) => liked.includes(x)).length > 0 ? 1 : -0.3);
    if (input.profile.colors.includes(i.color)) p += 0.1;
    p += 0.1 * clamp(input.affinity[i.id] ?? 0, -3, 3);
    s += clamp(p);
  }
  return s / parts.length;
}

export function diversityScore(r: Resolved, input: EngineInput): { score: number; reason?: string } {
  const parts = coreItems(r).concat(r.SHOES ? [r.SHOES] : []);
  if (parts.length === 0) return { score: 0 };
  let s = 0;
  let reason: string | undefined;
  for (const i of parts) {
    const since = i.lastWornAt ? diffDays(input.ctx.date, i.lastWornAt) : null;
    if (since !== null && since < 3) s += 0.2;
    else if (since !== null && since < 7) s += 0.6;
    else s += 1;
    if (!reason && i.category !== 'SHOES') {
      if (i.wornCount === 0) reason = `아직 한 번도 안 입은 ${josa(i.name, '을', '를')} 꺼내 봤어요`;
      else if (since !== null && since >= 14) reason = `${since}일 동안 안 입은 ${josa(i.name, '을', '를')} 살렸어요`;
    }
  }
  s /= parts.length;
  // 몸에 걸친 옷이 모두 최근 코디와 같으면(상의·하의(·아우터) 또는 원피스(·아우터)) 반복으로 본다
  const core = coreItems(r);
  for (const prev of input.recentOutfits) {
    const prevIds = new Set(GARMENT_SLOTS.map((k) => prev[k]).filter(Boolean));
    if (core.length > 0 && core.every((i) => prevIds.has(i.id))) {
      s -= 0.3;
      break;
    }
  }
  return { score: clamp(s), reason };
}

// ---------------------------------------------------------------- 평가·설명

export function resolve(items: OutfitItems, byId: Map<string, WardrobeItem>): Resolved {
  const r: Resolved = {};
  for (const [slot, id] of Object.entries(items) as [Slot, string | undefined][]) {
    const it = id ? byId.get(id) : undefined;
    if (it) r[slot] = it;
  }
  return r;
}

export function evaluate(outfitItems: OutfitItems, input: EngineInput): Outfit {
  const byId = new Map(input.items.map((i) => [i.id, i]));
  const r = resolve(outfitItems, byId);
  const { ctx } = input;

  const c = colorScore(r);
  const o = occasionScore(r, ctx);
  const w = ctx.weather ? weatherScore(r, ctx.weather) : null;
  const t = tasteScore(r, input);
  const d = diversityScore(r, input);

  const breakdown: ScoreBreakdown = {
    color: c.score,
    occasion: o.score,
    weather: w ? w.score : null,
    taste: t,
    diversity: d.score,
  };

  // 날씨 데이터가 없으면 날씨 항목을 빼고 나머지 가중치를 재조정한다
  const total =
    WEIGHTS.color + WEIGHTS.occasion + WEIGHTS.taste + WEIGHTS.diversity + (w ? WEIGHTS.weather : 0);
  const raw =
    WEIGHTS.color * c.score +
    WEIGHTS.occasion * o.score +
    WEIGHTS.taste * t +
    WEIGHTS.diversity * d.score +
    (w ? WEIGHTS.weather * w.score : 0);
  const score = Math.round((raw / total) * 100);

  const reasons: string[] = [];
  const anchor = ctx.anchorItemId ? byId.get(ctx.anchorItemId) : undefined;
  if (anchor) reasons.push(`중심 아이템 ${josa(anchor.name, '을', '를')} 살린 조합이에요`);
  if (c.reason) reasons.push(c.reason);
  if (w?.reason) reasons.push(w.reason);
  if (d.reason) reasons.push(d.reason);
  if (reasons.length < 3 && o.reason) reasons.push(o.reason);

  return { id: outfitId(outfitItems), items: outfitItems, score, breakdown, reasons: reasons.slice(0, 3), context: ctx };
}

/** 코디 id. 원피스가 없으면 예전과 같은 모양(상의.하의.아우터.신발.가방)이라 저장된 id가 그대로 맞는다 */
function outfitId(items: OutfitItems): string {
  const keys = items.DRESS ? ID_SLOTS : ID_SLOTS.filter((k) => k !== 'DRESS');
  return keys.map((k) => items[k] ?? '-').join('.');
}

// ---------------------------------------------------------------- 후보 생성

function eligibleBySlot(input: EngineInput): Record<Slot, WardrobeItem[]> {
  const { ctx } = input;
  const pinned = new Set([...Object.values(ctx.fixed ?? {}), ctx.anchorItemId].filter(Boolean) as string[]);
  const out: Record<Slot, WardrobeItem[]> = { TOP: [], BOTTOM: [], DRESS: [], OUTER: [], SHOES: [], BAG: [] };

  for (const i of input.items) {
    const slot = slotOf(i.category);
    if (!slot) continue;
    if (isRetired(i)) continue; // 사용하지 않는 옷은 고정·중심 아이템이어도 뺀다 (doc/13, 서버는 400)
    if (pinned.has(i.id)) {
      out[slot].push(i);
      continue;
    }
    if (!isRecommendable(i.status)) continue; // 세탁·수선 중 아이템은 기본 제외 (ITEM_STATUS recommendable)
    if (ctx.avoidColors?.includes(i.color)) continue;
    out[slot].push(i);
  }

  // 최근 착용 제외는 옷에만 적용한다. 신발·가방은 매일 같은 것을 드는 경우가 많다
  if (ctx.excludeRecent) {
    for (const slot of GARMENT_SLOTS) {
      const fresh = out[slot].filter(
        (i) => pinned.has(i.id) || !i.lastWornAt || diffDays(ctx.date, i.lastWornAt) >= 3,
      );
      if (fresh.length > 0 || !REQUIRED.includes(slot)) out[slot] = fresh;
    }
  }

  // 고정·중심 아이템이 있으면 그 칸은 해당 아이템으로만 구성
  for (const id of pinned) {
    const it = input.items.find((i) => i.id === id);
    const slot = it && !isRetired(it) && slotOf(it.category);
    if (it && slot) out[slot] = [it];
  }
  return out;
}

/**
 * "최근 착용 아이템 제외"로 후보에서 빠지는 아이템 id (ctx.excludeRecent일 때만).
 * 엔진의 후보 풀과 같은 규칙이다: 옷 칸(상의·하의·원피스·아우터)만, 3일 안에 입은 옷,
 * 고정·중심 아이템은 빼지 않고, 상의·하의가 모두 최근에 입은 옷이면 그 칸은 빼지 않는다.
 * AI 코디 추천 요청의 excludeItemIds로 보낸다(doc/09 3장).
 */
export function recentExclusions(input: EngineInput): string[] {
  if (!input.ctx.excludeRecent) return [];
  const kept = new Set(Object.values(eligibleBySlot(input)).flat().map((i) => i.id));
  const all = eligibleBySlot({ ...input, ctx: { ...input.ctx, excludeRecent: false } });
  return Object.values(all)
    .flat()
    .filter((i) => !kept.has(i.id))
    .map((i) => i.id);
}

function differsBy(a: OutfitItems, b: OutfitItems): number {
  return (['TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES'] as Slot[]).filter((k) => a[k] !== b[k]).length;
}

/**
 * 몸판 후보: 상의×하의 조합과 원피스 한 벌.
 * 상의·하의를 고정(또는 중심 아이템)하면 원피스를 빼고, 원피스를 고정하면 상의·하의 조합을 뺀다.
 */
function bodyCandidates(pool: Record<Slot, WardrobeItem[]>, input: EngineInput): OutfitItems[] {
  const { ctx } = input;
  const anchor = ctx.anchorItemId ? input.items.find((i) => i.id === ctx.anchorItemId) : undefined;
  const anchorSlot = anchor ? slotOf(anchor.category) : null;
  const wantsDress = !!ctx.fixed?.DRESS || anchorSlot === 'DRESS';
  const wantsSeparates = !!ctx.fixed?.TOP || !!ctx.fixed?.BOTTOM || anchorSlot === 'TOP' || anchorSlot === 'BOTTOM';

  const bodies: OutfitItems[] = [];
  if (!wantsDress) for (const top of pool.TOP) for (const bottom of pool.BOTTOM) bodies.push({ TOP: top.id, BOTTOM: bottom.id });
  if (!wantsSeparates) for (const dress of pool.DRESS) bodies.push({ DRESS: dress.id });
  return bodies;
}

export function recommend(input: EngineInput, count = 3): Outfit[] {
  const pool = eligibleBySlot(input);
  const bodies = bodyCandidates(pool, input);
  if (bodies.length === 0) return [];

  const fixedOuter = input.ctx.fixed?.OUTER;
  const outers: (WardrobeItem | undefined)[] = fixedOuter ? pool.OUTER : [undefined, ...pool.OUTER];
  const shoes: (WardrobeItem | undefined)[] = pool.SHOES.length ? pool.SHOES : [undefined];

  const byId = new Map(input.items.map((i) => [i.id, i]));
  const scored: Outfit[] = [];
  for (const body of bodies)
    for (const outer of outers)
      for (const shoe of shoes) {
        const base: OutfitItems = { ...body, OUTER: outer?.id, SHOES: shoe?.id };
        // 가방은 조합 수를 줄이기 위해 색 조화가 가장 좋은 것 하나만 붙인다
        let best = base;
        if (pool.BAG.length) {
          let bestScore = -1;
          for (const bag of pool.BAG) {
            const cand = { ...base, BAG: bag.id };
            const sc = colorScore(resolve(cand, byId)).score;
            if (sc > bestScore) {
              bestScore = sc;
              best = cand;
            }
          }
        }
        scored.push(evaluate(best, input));
      }

  scored.sort((a, b) => b.score - a.score);

  // 품질·다양성 균형: 서로 2칸 이상 다른 코디를 우선 선정
  const picked: Outfit[] = [];
  for (const minDiff of [2, 1]) {
    for (const o of scored) {
      if (picked.length >= count) break;
      if (picked.some((p) => p.id === o.id)) continue;
      if (picked.every((p) => differsBy(p.items, o.items) >= minDiff)) picked.push(o);
    }
  }
  return picked;
}

/** 한 칸만 교체할 후보 (교체 후 점수순) */
export function alternativesFor(
  slot: Slot,
  current: OutfitItems,
  input: EngineInput,
): { item: WardrobeItem | null; outfit: Outfit }[] {
  const options: { item: WardrobeItem | null; outfit: Outfit }[] = [];
  for (const it of input.items) {
    if (slotOf(it.category) !== slot || it.id === current[slot] || !isCandidate(it)) continue;
    options.push({ item: it, outfit: evaluate({ ...current, [slot]: it.id }, input) });
  }
  if ((slot === 'OUTER' || slot === 'BAG') && current[slot]) {
    options.push({ item: null, outfit: evaluate({ ...current, [slot]: undefined }, input) });
  }
  return options.sort((a, b) => b.outfit.score - a.outfit.score);
}
