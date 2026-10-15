// 규칙 기반 코디 추천 엔진 (기획서 p.24 초기 점수 모델)
// 조건 입력 → 옷장 조회 → 후보 생성 → 점수 평가 → 상위 선정 → 설명 생성
import {
  COLOR,
  FORMALITY_LABEL,
  FORMALITY_LEVEL,
  GOOD_PAIRS,
  OCCASION_FORMALITY,
  OCCASION_LABEL,
  SUBTYPE,
} from '@/data/constants';
import type {
  Category,
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
import { diffDays } from './date';
import { josa } from './josa';

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

export function slotOf(category: Category): Slot | null {
  return category === 'ACC' ? null : category;
}

export function canRecommend(items: WardrobeItem[]): { ok: boolean; missing: string[] } {
  const owned = items.filter((i) => i.status === 'OWNED');
  const missing: string[] = [];
  if (!owned.some((i) => i.category === 'TOP')) missing.push('상의');
  if (!owned.some((i) => i.category === 'BOTTOM')) missing.push('하의');
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

function coreItems(r: Resolved): WardrobeItem[] {
  return [r.TOP, r.BOTTOM, r.OUTER].filter(Boolean) as WardrobeItem[];
}

function isGoodPair(a: WardrobeItem['color'], b: WardrobeItem['color']) {
  return GOOD_PAIRS.some(([x, y]) => (x === a && y === b) || (x === b && y === a));
}

// ---------------------------------------------------------------- 점수 항목

export function colorScore(r: Resolved): { score: number; reason?: string } {
  const core = coreItems(r);
  let s = 0.6;
  let reason: string | undefined;

  const distinct = [...new Set(core.map((i) => i.color))];
  for (let a = 0; a < distinct.length; a++) {
    for (let b = a + 1; b < distinct.length; b++) {
      if (isGoodPair(distinct[a], distinct[b])) {
        s += 0.15;
        reason ??= `${COLOR[distinct[a]].label}×${COLOR[distinct[b]].label} 조합이 안정적이에요`;
      }
    }
  }

  const accents = distinct.filter((c) => !COLOR[c].neutral);
  if (accents.length >= 2) s -= 0.25;
  if (accents.length === 1 && distinct.length >= 2) {
    s += 0.05;
    reason ??= `${josa(COLOR[accents[0]].label, '을', '를')} 포인트 컬러로 하나만 살렸어요`;
  }
  if (accents.length === 0 && distinct.length >= 2) {
    s += 0.1;
    reason ??= '뉴트럴 톤온톤으로 차분하게 맞췄어요';
  }

  if (r.TOP && r.BOTTOM) {
    if (r.TOP.color === r.BOTTOM.color && !COLOR[r.TOP.color].neutral) s -= 0.15;
    if (Math.abs(COLOR[r.TOP.color].light - COLOR[r.BOTTOM.color].light) >= 0.3) s += 0.1;
  }

  const patterned = core.filter((i) => i.pattern !== 'SOLID').length;
  if (patterned >= 2) s -= 0.2;

  if (r.SHOES && r.BAG) {
    const sameFamily =
      r.SHOES.color === r.BAG.color ||
      (COLOR[r.SHOES.color].light < 0.35 && COLOR[r.BAG.color].light < 0.35);
    if (sameFamily) s += 0.05;
  }

  return { score: clamp(s), reason };
}

const MOOD_STYLES: Record<RecommendContext['mood'], StyleTag[]> = {
  NEAT: ['NEAT', 'CLASSIC', 'MINIMAL'],
  COMFY: ['CASUAL'],
  CHIC: ['MINIMAL', 'CLASSIC'],
};

export function occasionScore(r: Resolved, ctx: RecommendContext): { score: number; reason?: string } {
  const target = (OCCASION_FORMALITY[ctx.occasion] + FORMALITY_LEVEL[ctx.formality]) / 2;
  const parts = [r.TOP, r.BOTTOM, r.OUTER, r.SHOES].filter(Boolean) as WardrobeItem[];
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
  const warmth = coreItems(r).reduce((sum, i) => sum + SUBTYPE[i.subType].warmth, 0);
  s -= 0.08 * Math.abs(warmth - needed);

  const season = seasonsFor(w.temp);
  for (const i of coreItems(r)) s += i.seasons.some((x) => season.includes(x)) ? 0.03 : -0.1;

  if (w.rainProb >= 60 && r.SHOES) {
    if (COLOR[r.SHOES.color].light > 0.7) s -= 0.15;
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
  const ids = Object.values(r).map((i) => i?.id);
  for (const prev of input.recentOutfits) {
    const same = (['TOP', 'BOTTOM', 'OUTER'] as Slot[]).filter((k) => prev[k] && ids.includes(prev[k])).length;
    if (same >= 3 || (same >= 2 && !r.OUTER)) {
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

  const id = (['TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'] as Slot[]).map((k) => outfitItems[k] ?? '-').join('.');
  return { id, items: outfitItems, score, breakdown, reasons: reasons.slice(0, 3), context: ctx };
}

// ---------------------------------------------------------------- 후보 생성

function eligibleBySlot(input: EngineInput): Record<Slot, WardrobeItem[]> {
  const { ctx } = input;
  const pinned = new Set([...Object.values(ctx.fixed ?? {}), ctx.anchorItemId].filter(Boolean) as string[]);
  const out: Record<Slot, WardrobeItem[]> = { TOP: [], BOTTOM: [], OUTER: [], SHOES: [], BAG: [] };

  for (const i of input.items) {
    const slot = slotOf(i.category);
    if (!slot) continue;
    if (pinned.has(i.id)) {
      out[slot].push(i);
      continue;
    }
    if (i.status !== 'OWNED') continue; // 세탁·수선 중 아이템은 기본 제외
    if (ctx.avoidColors?.includes(i.color)) continue;
    out[slot].push(i);
  }

  // 최근 착용 제외는 옷에만 적용한다. 신발·가방은 매일 같은 것을 드는 경우가 많다
  if (ctx.excludeRecent) {
    for (const slot of ['TOP', 'BOTTOM', 'OUTER'] as Slot[]) {
      const fresh = out[slot].filter(
        (i) => pinned.has(i.id) || !i.lastWornAt || diffDays(ctx.date, i.lastWornAt) >= 3,
      );
      if (fresh.length > 0 || !REQUIRED.includes(slot)) out[slot] = fresh;
    }
  }

  // 고정·중심 아이템이 있으면 그 칸은 해당 아이템으로만 구성
  for (const id of pinned) {
    const it = input.items.find((i) => i.id === id);
    const slot = it && slotOf(it.category);
    if (it && slot) out[slot] = [it];
  }
  return out;
}

function differsBy(a: OutfitItems, b: OutfitItems): number {
  return (['TOP', 'BOTTOM', 'OUTER', 'SHOES'] as Slot[]).filter((k) => a[k] !== b[k]).length;
}

export function recommend(input: EngineInput, count = 3): Outfit[] {
  const pool = eligibleBySlot(input);
  if (pool.TOP.length === 0 || pool.BOTTOM.length === 0) return [];

  const fixedOuter = input.ctx.fixed?.OUTER;
  const outers: (WardrobeItem | undefined)[] = fixedOuter ? pool.OUTER : [undefined, ...pool.OUTER];
  const shoes: (WardrobeItem | undefined)[] = pool.SHOES.length ? pool.SHOES : [undefined];

  const byId = new Map(input.items.map((i) => [i.id, i]));
  const scored: Outfit[] = [];
  for (const top of pool.TOP)
    for (const bottom of pool.BOTTOM)
      for (const outer of outers)
        for (const shoe of shoes) {
          const base: OutfitItems = { TOP: top.id, BOTTOM: bottom.id, OUTER: outer?.id, SHOES: shoe?.id };
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
    if (slotOf(it.category) !== slot || it.id === current[slot] || it.status !== 'OWNED') continue;
    options.push({ item: it, outfit: evaluate({ ...current, [slot]: it.id }, input) });
  }
  if ((slot === 'OUTER' || slot === 'BAG') && current[slot]) {
    options.push({ item: null, outfit: evaluate({ ...current, [slot]: undefined }, input) });
  }
  return options.sort((a, b) => b.outfit.score - a.outfit.score);
}
