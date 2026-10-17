// 착장 진단·처방 (기획서 p.25): 5개 항목 평가 → 강점 1개 · 개선점 1개 → 처방
import { SLOTS } from '@/data/constants';
import type { AxisScores, Diagnosis, OutfitItems } from '@/data/types';
import { subTypeMeta } from './codes';
import {
  alternativesFor,
  colorScore,
  diversityScore,
  occasionScore,
  resolve,
  weatherScore,
  type EngineInput,
  type Resolved,
} from './recommend';

export const AXIS_LABEL: Record<keyof AxisScores, string> = {
  color: '색상 조화',
  silhouette: '실루엣 · 핏',
  tpo: 'TPO 적합',
  weather: '날씨 적합',
  diversity: '다양성 · 반복',
};

const AXIS_WEIGHT: AxisScores = { color: 0.3, silhouette: 0.15, tpo: 0.25, weather: 0.2, diversity: 0.1 };

const TIPS: Record<keyof AxisScores, string> = {
  color: '포인트 색은 한 가지만 두고, 나머지는 뉴트럴 톤으로 맞춰 보세요.',
  silhouette: '상의 앞자락을 살짝 넣어 입으면(하프 턱) 상하의 비율이 정리돼요.',
  tpo: '셔츠 단추를 하나 더 채우고 로퍼를 신으면 격식이 한 단계 올라가요.',
  weather: '얇은 레이어를 하나 더하면 아침저녁 일교차에 대응하기 좋아요.',
  diversity: '자주 입은 아이템 대신, 잠자는 옷을 하나 꺼내 조합해 보세요.',
};

/** 원피스 코디에서 실루엣이 약점일 때의 팁 (하프 턱 대신) */
const DRESS_SILHOUETTE_TIP = '허리에 벨트를 하나 더하면 원피스 라인이 정리되고 비율이 좋아 보여요.';

export function silhouetteScore(r: Resolved): number {
  const vol = (i: NonNullable<Resolved['TOP']>) => subTypeMeta(i.subType).volume;
  if (r.DRESS && !r.TOP && !r.BOTTOM) {
    // 원피스는 위아래가 이어져 비율이 정돈된다. 볼륨 원피스에 볼륨 아우터를 겹치면 부해 보인다
    const dress = vol(r.DRESS);
    let s = dress === 1 ? 0.8 : 0.9;
    if (r.OUTER && vol(r.OUTER) === 1 && dress === 1) s -= 0.15;
    return s;
  }
  if (!r.TOP || !r.BOTTOM) return 0.6;
  const top = vol(r.TOP);
  const bottom = vol(r.BOTTOM);
  let s = 0.85;
  if (top === 1 && bottom === 1) s = 0.55; // 위아래 모두 볼륨
  else if (top !== bottom && (top === 1 || bottom === 1)) s = 0.95; // 한쪽만 볼륨 — 균형
  else if (top === -1 && bottom === -1) s = 0.8;
  if (r.OUTER && vol(r.OUTER) === 1 && bottom === 1) s -= 0.15;
  return Math.max(0, Math.min(1, s));
}

function axesFor(r: Resolved, input: EngineInput): AxisScores {
  return {
    color: colorScore(r).score,
    silhouette: silhouetteScore(r),
    tpo: occasionScore(r, input.ctx).score,
    weather: input.ctx.weather ? weatherScore(r, input.ctx.weather).score : 0.75,
    diversity: diversityScore(r, input).score,
  };
}

function total(a: AxisScores): number {
  const keys = Object.keys(a) as (keyof AxisScores)[];
  return Math.round(keys.reduce((s, k) => s + a[k] * AXIS_WEIGHT[k], 0) * 100);
}

export function diagnose(items: OutfitItems, input: EngineInput): Diagnosis {
  const byId = new Map(input.items.map((i) => [i.id, i]));
  const r = resolve(items, byId);
  const axes = axesFor(r, input);
  const aiScore = total(axes);

  const keys = Object.keys(axes) as (keyof AxisScores)[];
  const strength = keys.reduce((a, b) => (axes[b] > axes[a] ? b : a));
  const weakness = keys.reduce((a, b) => (axes[b] < axes[a] ? b : a));

  // 처방 1순위: 내 옷장에서 약점 아이템 1개를 교체해 진단 점수가 가장 오르는 조합
  let swap: Diagnosis['swap'];
  let bestGain = 2;
  for (const slot of SLOTS) {
    if (!items[slot]) continue;
    for (const alt of alternativesFor(slot, items, input).slice(0, 4)) {
      // 사용자가 교체해 뺐거나 '별로예요'로 평가한 옷은 다시 권하지 않는다
      if (!alt.item || (input.affinity[alt.item.id] ?? 0) < 0) continue;
      const next = { ...items, [slot]: alt.item.id };
      const gain = total(axesFor(resolve(next, byId), input)) - aiScore;
      if (gain > bestGain) {
        bestGain = gain;
        swap = { slot, fromId: items[slot]!, toId: alt.item.id };
      }
    }
  }

  const tip = weakness === 'silhouette' && r.DRESS ? DRESS_SILHOUETTE_TIP : TIPS[weakness];
  return { aiScore, axes, strength, weakness, swap, tip };
}
