import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { OutfitBoard, OutfitItemRows, ReasonBox } from '@/components/OutfitView';
import { Button, Header, Row, Screen, Serif } from '@/components/ui';
import { FORMALITY_LABEL, MOOD_LABEL, OCCASION_LABEL, STYLE_LABEL } from '@/data/constants';
import type { Outfit, ScoreBreakdown } from '@/data/types';
import { newId } from '@/lib/context';
import { longLabel, todayKey } from '@/lib/date';
import { WEIGHTS } from '@/lib/recommend';
import { engineInput, useStore } from '@/store/useStore';
import { colors } from '@/theme';

const BREAKDOWN_LABEL: Record<keyof ScoreBreakdown, string> = {
  color: '색상 조화',
  occasion: '상황 적합',
  weather: '날씨 적합',
  taste: '개인 취향',
  diversity: '다양성',
};

// 목업 ④ 추천 결과 3안 (p.42)
export default function Result() {
  const draft = useStore((s) => s.draft);
  const saveDayOutfit = useStore((s) => s.saveDayOutfit);
  const addPlan = useStore((s) => s.addPlan);
  const [outfits, setOutfits] = useState<Outfit[]>(draft?.outfits ?? []);
  const [index, setIndex] = useState(0);
  const current = outfits[index];
  const ctx = current?.context;
  const input = useMemo(() => (ctx ? engineInput(ctx) : null), [ctx]);

  if (!draft || !current || !ctx || !input) {
    return (
      <Screen header={<Header title="추천 결과" />}>
        <Text style={{ color: colors.sub, marginTop: 40, textAlign: 'center' }}>추천 결과가 없어요. AI 코디에서 다시 시도해 주세요.</Text>
      </Screen>
    );
  }

  const kicker =
    draft.mode === 'PLAN' && draft.plan
      ? `${longLabel(draft.plan.date)} · ${OCCASION_LABEL[ctx.occasion]} · ${draft.plan.concepts.map((c) => STYLE_LABEL[c]).join('·')}`
      : `${OCCASION_LABEL[ctx.occasion]} · ${MOOD_LABEL[ctx.mood]} · ${FORMALITY_LABEL[ctx.formality]}`;

  const save = () => {
    if (draft.mode === 'PLAN' && draft.plan) {
      addPlan({ ...draft.plan, id: newId('plan'), outfit: current });
      router.dismissTo('/calendar');
    } else {
      saveDayOutfit(todayKey(), current);
      router.dismissTo('/home');
    }
  };

  return (
    <Screen header={<Header title="추천 결과" />} footer={<Button title={draft.mode === 'PLAN' ? `코디 ${index + 1} 캘린더에 저장` : `코디 ${index + 1}을 오늘 코디로 저장`} onPress={save} />}>
      <Text style={s.kicker}>{kicker}</Text>
      <Serif style={s.title}>내 옷으로 만든 {outfits.length}가지 코디</Serif>

      <View style={s.tabs}>
        {outfits.map((o, i) => (
          <Pressable key={o.id} onPress={() => setIndex(i)} style={[s.tab, i === index && s.tabOn]}>
            <Text style={[s.tabText, i === index && { color: colors.ink }]}>코디 {i + 1}</Text>
            <Text style={[s.tabScore, i === index && { color: colors.green }]}>{o.score}점</Text>
          </Pressable>
        ))}
      </View>

      <OutfitBoard outfit={current} />
      <ReasonBox reasons={current.reasons} />

      {/* 점수 근거: 기획서 p.24 초기 점수 모델 */}
      <View style={s.breakdown}>
        {(Object.keys(BREAKDOWN_LABEL) as (keyof ScoreBreakdown)[]).map((k) => {
          const v = current.breakdown[k];
          return (
            <Row key={k} style={{ gap: 10 }}>
              <Text style={s.bLabel}>{BREAKDOWN_LABEL[k]}</Text>
              <Text style={s.bWeight}>{Math.round(WEIGHTS[k] * 100)}%</Text>
              <View style={s.bTrack}>{v !== null && <View style={[s.bFill, { width: `${Math.round(v * 100)}%` }]} />}</View>
              <Text style={s.bValue}>{v === null ? '제외' : Math.round(v * 100)}</Text>
            </Row>
          );
        })}
      </View>

      <View style={{ marginTop: 10 }}>
        <OutfitItemRows outfit={current} input={input} onChange={(next) => setOutfits((list) => list.map((o, i) => (i === index ? next : o)))} />
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  kicker: { color: colors.green, fontSize: 12, fontWeight: '700', letterSpacing: 1, marginTop: 6 },
  title: { fontSize: 24, marginTop: 6 },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.line, marginTop: 18, marginBottom: 16 },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 10 },
  tabOn: { borderBottomWidth: 2, borderBottomColor: colors.green, marginBottom: -1 },
  tabText: { fontSize: 15, fontWeight: '800', color: colors.faint },
  tabScore: { fontSize: 11, color: colors.faint, marginTop: 2, fontWeight: '600' },
  breakdown: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 14, gap: 8, marginTop: 12 },
  bLabel: { width: 64, fontSize: 12, color: colors.ink, fontWeight: '600' },
  bWeight: { width: 30, fontSize: 11, color: colors.faint },
  bTrack: { flex: 1, height: 6, backgroundColor: colors.tile },
  bFill: { height: 6, backgroundColor: colors.green },
  bValue: { width: 30, textAlign: 'right', fontSize: 12, fontWeight: '700', color: colors.ink },
});
