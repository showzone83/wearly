// AI 코디 추천 실행 (doc/09_AI_코디_추천.md 5장): 서버 호출 → 엔진 점수 → 결과/오류.
// 요청 만들기·응답 처리는 src/lib/aiOutfits.ts(순수 로직)에 있다.
import { isApiError } from '@/api/errors';
import type { Outfit, RecommendContext, TimeOfDay } from '@/data/types';
import { engineInput, useStore } from '@/store/useStore';
import { isAccountMode, syncWardrobe } from './account';
import { buildRecommendRequest, missingSlotsText, rankCandidates, SHOW_COUNT, type DatedOutfit, type RecommendSource } from './aiOutfits';
import { api, errorMessage } from './api';
import { systemTraceId } from './errorText';
import { MOCK_MODE } from './mode';
import { recommend } from './recommend';

export type AiPhase = 'look' | 'pick' | 'score';

export type AiOutcome =
  | { kind: 'ok'; outfits: Outfit[]; source: RecommendSource }
  /** 429 AI_LIMIT_EXCEEDED */
  | { kind: 'limit'; message: string }
  /** 400 WARDROBE_INSUFFICIENT. missing은 모자란 칸 안내 */
  | { kind: 'insufficient'; missing: string }
  /** 502·네트워크·그 밖의 실패. 규칙 엔진으로 추천 받기를 권한다 */
  | { kind: 'error'; message: string; traceId?: string };

/** 규칙 엔진 추천 (홈 탭과 같은 방식). 조건에 맞는 옷이 없으면 빈 배열 */
export function ruleOutfits(ctx: RecommendContext): Outfit[] {
  return recommend(engineInput(ctx), SHOW_COUNT);
}

function history(): DatedOutfit[] {
  const s = useStore.getState();
  return [
    ...Object.entries(s.dayOutfits).map(([date, saved]) => ({ date, items: saved.outfit.items })),
    ...s.looks.map((l) => ({ date: l.date, items: l.items })),
  ];
}

function ruleOutcome(ctx: RecommendContext, note: string): AiOutcome {
  const outfits = ruleOutfits(ctx);
  if (outfits.length === 0) return { kind: 'error', message: '조건에 맞는 옷이 부족해요. 피하고 싶은 색이나 중심 아이템을 줄여 보세요.' };
  return { kind: 'ok', outfits, source: { method: 'RULE', note } };
}

/**
 * AI 코디 추천을 받는다.
 * - 계정 모드: 서버 AI 후보 → 엔진 점수 → 상위 3개.
 * - 목업 실행(MOCK_MODE)에서 게스트이거나 서버에 닿지 못하면 규칙 엔진으로 바로 추천한다.
 * - 기본 실행의 실패는 목업으로 가리지 않고 오류로 돌려준다(화면이 "규칙 엔진으로 추천 받기"를 따로 준다).
 */
export async function requestAiOutfits(
  ctx: RecommendContext,
  options: { plan?: { timeOfDay?: TimeOfDay; memo?: string }; onPhase?: (p: AiPhase) => void } = {},
): Promise<AiOutcome> {
  const { plan, onPhase } = options;
  if (!isAccountMode()) {
    if (MOCK_MODE) return ruleOutcome(ctx, '게스트 체험이라 규칙 엔진으로 추천했어요');
    return { kind: 'error', message: '로그인해야 AI 추천을 쓸 수 있어요.' };
  }

  onPhase?.('look');
  const req = buildRecommendRequest({ input: engineInput(ctx), history: history(), plan });
  onPhase?.('pick');

  let res;
  try {
    res = await api.outfits.recommend(req);
  } catch (e) {
    console.warn('[aiRecommend] AI 코디 추천 실패', e);
    if (isApiError(e) && e.code === 'AI_LIMIT_EXCEEDED') {
      return { kind: 'limit', message: '오늘 AI 코디 추천을 모두 썼어요. 내일 다시 쓸 수 있어요.' };
    }
    if (isApiError(e) && e.code === 'WARDROBE_INSUFFICIENT') {
      return { kind: 'insufficient', missing: missingSlotsText(e.params) || '추천에 쓸 수 있는 옷이 모자라요.' };
    }
    if (isApiError(e) && e.code === 'NETWORK' && MOCK_MODE) {
      return ruleOutcome(ctx, '서버에 연결하지 못해 규칙 엔진으로 추천했어요');
    }
    if (isApiError(e) && e.status === 404) {
      // 이 API가 없는 예전 서버 (경로 없음)
      return { kind: 'error', message: '서버에 AI 코디 추천 기능이 아직 없어요. 서버를 최신으로 올린 뒤 다시 시도해 주세요.' };
    }
    return { kind: 'error', message: errorMessage(e, '잠시 후 다시 시도해 주세요.'), traceId: systemTraceId(e) };
  }

  onPhase?.('score');
  let ranked = rankCandidates(res.candidates, engineInput(ctx));
  if (ranked.stale.length > 0) {
    // 다른 기기에서 옷을 바꿨거나 지운 경우: 서버 목록으로 옷장을 한 번 맞추고 같은 후보를 다시 채점한다
    if (await syncWardrobe()) ranked = rankCandidates(res.candidates, engineInput(ctx));
  }

  if (ranked.outfits.length === 0) {
    return {
      kind: 'error',
      message:
        res.candidates.length === 0
          ? 'AI가 조건에 맞는 코디를 찾지 못했어요. 조건을 바꿔 보거나 규칙 엔진으로 추천 받아 보세요.'
          : 'AI가 고른 옷을 이 기기 옷장에서 찾지 못했어요. 잠시 후 다시 시도해 주세요.',
    };
  }
  return {
    kind: 'ok',
    outfits: ranked.outfits,
    source: {
      method: 'AI',
      provider: String(res.provider ?? ''),
      model: res.model ?? null,
      remainingToday: res.remainingToday,
      dropped: ranked.dropped,
    },
  };
}
