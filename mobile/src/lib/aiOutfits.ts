// AI 코디 추천 (doc/09_AI_코디_추천.md): 요청 만들기와 응답 처리.
// 서버가 AI로 고른 후보를 앱 규칙 엔진 evaluate()로 같은 5개 기준 점수를 매겨 높은 순으로 보여 준다.
// 서버 호출·저장소는 src/lib/aiRecommend.ts가 맡는다. 이 파일은 Node(검증 스크립트)에서도 돌도록
// React Native·Expo·저장소를 import하지 않는다.
import type { OutfitCandidateDto, OutfitRecommendRequest, OutfitSlotItems } from '@/api/types';
import { SLOTS } from '@/data/constants';
import type { Outfit, OutfitItems, Slot, TimeOfDay } from '@/data/types';
import { slotLabel } from './codes';
import { diffDays } from './date';
import { evaluate, recentExclusions, slotOf, type EngineInput } from './recommend';

/** 서버에 요청하는 후보 수 (계약 기본값) */
export const CANDIDATE_COUNT = 5;
/** 결과 화면에 보여 주는 코디 수 */
export const SHOW_COUNT = 3;
/** 최근 코디로 보내는 기간(일). 엔진의 반복 판단(engineInput)과 같다 */
const RECENT_DAYS = 7;
const MEMO_MAX = 200;

/** 추천 결과의 출처. 결과 화면 배지에 쓴다 */
export type RecommendSource =
  | {
      method: 'AI';
      /** 공급자 ID (claude·openai·gemini, 서버 목업이면 mock) */
      provider: string;
      model: string | null;
      /** 오늘 남은 AI 코디 추천 횟수 */
      remainingToday: number;
      /** 서버 후보 중 이 기기 옷장과 맞지 않아 버린 수 */
      dropped: number;
    }
  | {
      method: 'RULE';
      /** 규칙 엔진으로 추천한 까닭 (게스트 체험·서버 연결 실패 등). 사용자가 직접 고른 경우는 없음 */
      note?: string;
    };

/** 알려진 공급자 ID의 표시 이름. 그 밖의 ID는 그대로 보여 준다 */
const PROVIDER_NAME: Record<string, string> = { claude: 'Claude', openai: 'OpenAI', gemini: 'Gemini' };

export function providerLabel(id: string): string {
  return PROVIDER_NAME[id.toLowerCase()] ?? (id || '알 수 없음');
}

// ---------------------------------------------------------------- 요청

/** 날짜가 붙은 지난 코디 (저장한 오늘 코디·착용 기록) */
export interface DatedOutfit {
  date: string;
  items: OutfitItems;
}

export interface RequestSource {
  /** engineInput(ctx): 옷장·취향·조건 */
  input: EngineInput;
  /** 저장한 코디와 착용 기록 전체. 최근 7일만 골라 보낸다 */
  history: DatedOutfit[];
  /** 예정일 코디만: 시간대·메모 */
  plan?: { timeOfDay?: TimeOfDay; memo?: string };
  count?: number;
}

/** 앱에 있는 아이템 id만 남긴 칸별 id (지운 옷이 남은 기록 정리) */
function knownItems(items: OutfitItems, known: Set<string>): OutfitSlotItems {
  const out: OutfitSlotItems = {};
  for (const slot of SLOTS) {
    const id = items[slot];
    if (id && known.has(id)) out[slot] = id;
  }
  return out;
}

/** coordi 화면 조건 + 기기 기록으로 POST /outfits/recommendations 요청을 만든다 */
export function buildRecommendRequest({ input, history, plan, count = CANDIDATE_COUNT }: RequestSource): OutfitRecommendRequest {
  const { ctx } = input;
  const known = new Set(input.items.map((i) => i.id));

  // 최근 7일 코디: 기준 날짜 이전 1~7일. 같은 날 같은 조합(저장 코디와 착용 기록)은 한 번만
  const seen = new Set<string>();
  const recent: { date: string; items: OutfitSlotItems }[] = [];
  for (const h of [...history].sort((a, b) => b.date.localeCompare(a.date))) {
    const d = diffDays(ctx.date, h.date);
    if (d <= 0 || d > RECENT_DAYS) continue;
    const items = knownItems(h.items, known);
    if (Object.keys(items).length === 0) continue;
    const key = `${h.date}|${SLOTS.map((k) => items[k] ?? '-').join('.')}`;
    if (seen.has(key)) continue;
    seen.add(key);
    recent.push({ date: h.date, items });
  }

  const liked: string[] = [];
  const disliked: string[] = [];
  for (const [id, v] of Object.entries(input.affinity)) {
    if (!known.has(id) || !v) continue;
    (v > 0 ? liked : disliked).push(id);
  }

  const w = ctx.weather;
  const memo = plan?.memo?.trim().slice(0, MEMO_MAX);
  const fixed = ctx.fixed ? knownItems(ctx.fixed, known) : undefined;

  return {
    context: {
      date: ctx.date,
      occasion: ctx.occasion,
      mood: ctx.mood,
      formality: ctx.formality,
      // manual(직접 입력 여부)은 계약에 없어서 뺀다
      weather: w ? { temp: w.temp, min: w.min, max: w.max, rainProb: w.rainProb, cond: w.cond } : undefined,
      anchorItemId: ctx.anchorItemId && known.has(ctx.anchorItemId) ? ctx.anchorItemId : undefined,
      fixed: fixed && Object.keys(fixed).length ? fixed : undefined,
      avoidColors: ctx.avoidColors?.length ? [...ctx.avoidColors] : undefined,
      concepts: ctx.concepts?.length ? [...ctx.concepts] : undefined,
      timeOfDay: plan?.timeOfDay,
      memo: memo || undefined,
    },
    excludeItemIds: recentExclusions(input),
    recent,
    likedItemIds: liked,
    dislikedItemIds: disliked,
    count,
  };
}

// ---------------------------------------------------------------- 응답

export interface RankResult {
  /** 엔진 점수 높은 순(같으면 AI 순서) 상위 코디 */
  outfits: Outfit[];
  /** 이 기기 옷장에 없거나 칸이 다른 아이템 id (서버·기기 동기화 차이). 있으면 옷장을 다시 받아 본다 */
  stale: string[];
  /** 버린 후보 수 (동기화 차이·구성 오류·중복) */
  dropped: number;
}

/**
 * 서버 후보를 이 기기 옷장 기준으로 검사하고 엔진으로 점수를 매긴다.
 * - 기기에 없는 id, 아이템 종류와 다른 칸에 든 id가 있으면 그 후보를 버리고 stale에 모은다.
 * - 몸판이 상의+하의 또는 원피스 하나가 아니면 버린다(서버가 검증하지만 화면이 깨지지 않게 한 번 더).
 * - 같은 조합은 한 번만.
 */
export function rankCandidates(candidates: readonly OutfitCandidateDto[], input: EngineInput, count = SHOW_COUNT): RankResult {
  const byId = new Map(input.items.map((i) => [i.id, i]));
  const stale = new Set<string>();
  const seen = new Set<string>();
  const scored: Outfit[] = [];
  let dropped = 0;

  for (const cand of Array.isArray(candidates) ? candidates : []) {
    const raw = (cand && typeof cand.items === 'object' && cand.items) || {};
    const items: OutfitItems = {};
    let ok = true;
    for (const slot of SLOTS) {
      const id = (raw as Record<string, unknown>)[slot];
      if (id === undefined || id === null || id === '') continue;
      const item = typeof id === 'string' ? byId.get(id) : undefined;
      if (!item || slotOf(item.category) !== slot) {
        if (typeof id === 'string') stale.add(id);
        ok = false;
        continue;
      }
      items[slot as Slot] = id as string;
    }
    const separates = !!items.TOP && !!items.BOTTOM && !items.DRESS;
    const dress = !!items.DRESS && !items.TOP && !items.BOTTOM;
    if (!ok || !(separates || dress)) {
      dropped++;
      continue;
    }

    const outfit = evaluate(items, input);
    if (seen.has(outfit.id)) {
      dropped++;
      continue;
    }
    seen.add(outfit.id);
    const reason = typeof cand.reason === 'string' ? cand.reason.trim() : '';
    if (reason) outfit.ai = { reason, scores: cand.aiScores };
    scored.push(outfit);
  }

  // Array.prototype.sort는 안정 정렬이라 같은 점수면 AI 순서가 유지된다
  scored.sort((a, b) => b.score - a.score);
  return { outfits: scored.slice(0, count), stale: [...stale], dropped };
}

/**
 * WARDROBE_INSUFFICIENT의 message(모자란 칸)를 보여 줄 문구로 바꾼다.
 * 서버가 칸 코드(예: "SHOES")를 그대로 주면 이름(신발)으로 바꾼다.
 */
export function missingSlotsText(message: string): string {
  return message.replace(/\b(TOP|BOTTOM|DRESS|OUTER|SHOES|BAG)\b/g, (m) => slotLabel(m as Slot));
}
