import { router, useLocalSearchParams } from 'expo-router';
import { Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { OutfitMini } from '@/components/OutfitMini';
import { Button, Header, Kicker, Row, Screen, Stars, Title } from '@/components/ui';
import { EMOTION_LABEL, OCCASION_LABEL, OTHERS_LABEL } from '@/data/constants';
import type { AxisScores } from '@/data/types';
import { longLabel } from '@/lib/date';
import { josa } from '@/lib/josa';
import { AXIS_LABEL } from '@/lib/diagnose';
import { useStore } from '@/store/useStore';
import { cardBase, makeStyles, useColors } from '@/theme';

// 목업 ⑤ AI 진단 · 처방 (p.25): 5개 항목 → 강점 1 · 개선점 1 → 내 옷장 교체 · 스타일링 팁
export default function LookDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const look = useStore((s) => s.looks.find((l) => l.id === id));
  const items = useStore((s) => s.items);
  const s = useStyles();
  const colors = useColors();

  if (!look) {
    return (
      <Screen header={<Header title="AI 진단" />}>
        <Text style={{ color: colors.textSub, marginTop: 40, textAlign: 'center' }}>기록을 찾을 수 없어요.</Text>
      </Screen>
    );
  }

  const d = look.diagnosis;
  const from = d.swap && items.find((i) => i.id === d.swap!.fromId);
  const to = d.swap && items.find((i) => i.id === d.swap!.toId);
  const praised = look.praisedItemId && items.find((i) => i.id === look.praisedItemId);
  const othersScore = { PRAISE: 5, NORMAL: 3, MEH: 2 }[look.others];

  return (
    <Screen header={<Header title="AI 진단" onBack={() => router.dismissTo('/calendar')} />} footer={<Button title="캘린더로" variant="outline" onPress={() => router.dismissTo('/calendar')} />}>
      <Kicker color={colors.primary}>
        DAILY · {longLabel(look.date)} · {OCCASION_LABEL[look.occasion]}
      </Kicker>
      <Row style={{ alignItems: 'flex-end', gap: 10, marginTop: 10 }}>
        <Title style={s.score}>{d.aiScore}</Title>
        <Text style={s.scoreUnit}>점</Text>
        <View style={{ flex: 1 }} />
        <OutfitMini items={look.items} size={46} />
      </Row>
      <Text style={s.summary}>
        강점은 <Text style={s.em}>{AXIS_LABEL[d.strength]}</Text>, 개선점은 <Text style={[s.em, { color: colors.danger }]}>{AXIS_LABEL[d.weakness]}</Text>
        {josa(AXIS_LABEL[d.weakness], '이에요', '예요').slice(AXIS_LABEL[d.weakness].length)}.
      </Text>

      {/* 3축 비교 (p.18) */}
      <View style={s.compare}>
        <View style={s.cmpCol}>
          <Text style={s.cmpLabel}>AI 점수</Text>
          <Title style={s.cmpValue}>{d.aiScore}</Title>
        </View>
        <View style={s.cmpCol}>
          <Text style={s.cmpLabel}>나의 만족도</Text>
          <Stars value={look.selfScore} size={14} />
          <Text style={s.cmpSub}>{look.emotions.map((e) => EMOTION_LABEL[e]).join(' · ') || '-'}</Text>
        </View>
        <View style={s.cmpCol}>
          <Text style={s.cmpLabel}>주변 반응</Text>
          <Stars value={othersScore} size={14} />
          <Text style={s.cmpSub}>{OTHERS_LABEL[look.others]}</Text>
        </View>
      </View>
      {look.memo && <Text style={s.memo}>“{look.memo}”</Text>}

      <Text style={s.section}>Diagnosis · 진단</Text>
      <View style={{ gap: 10 }}>
        {(Object.keys(AXIS_LABEL) as (keyof AxisScores)[]).map((k) => {
          const v = Math.round(d.axes[k] * 100);
          const tone = k === d.weakness ? colors.danger : k === d.strength ? colors.primary : colors.text;
          return (
            <Row key={k} style={{ gap: 10 }}>
              <Text style={s.axis}>{AXIS_LABEL[k]}</Text>
              <View style={s.track}>
                <View style={[s.fill, { width: `${v}%`, backgroundColor: tone }]} />
              </View>
              <Text style={[s.axisVal, { color: tone }]}>{v}</Text>
            </Row>
          );
        })}
      </View>

      <Text style={s.section}>Prescription · 처방</Text>
      {from && to ? (
        <View style={s.rx}>
          <Text style={s.rxHead}>1 · 내 옷장에서 교체</Text>
          <Row style={{ gap: 10, marginTop: 10 }}>
            <ItemThumb item={from} size={64} />
            <Icon name="chevron" color={colors.primary} />
            <ItemThumb item={to} size={64} />
            <Text style={s.rxText}>
              {from.name} 대신 <Text style={s.em}>{to.name}</Text>
              {josa(to.name, '이면', '면').slice(to.name.length)} 점수가 더 올라가요.
            </Text>
          </Row>
        </View>
      ) : (
        <View style={s.rx}>
          <Text style={s.rxHead}>1 · 내 옷장에서 교체</Text>
          <Text style={[s.rxText, { marginTop: 6 }]}>지금 옷장 안에서는 이 조합이 가장 좋아요.</Text>
        </View>
      )}
      <View style={s.rx}>
        <Text style={s.rxHead}>2 · 스타일링 팁</Text>
        <Text style={[s.rxText, { marginTop: 6 }]}>{d.tip}</Text>
      </View>
      {(praised || look.selfScore >= 4) && (
        <View style={[s.rx, { backgroundColor: colors.primarySoft, borderColor: colors.primarySoft }]}>
          <Text style={[s.rxHead, { color: colors.primary }]}>다음 추천에 반영</Text>
          <Text style={[s.rxText, { marginTop: 6 }]}>
            {praised ? `칭찬받은 ${praised.name} 활용 조합의 가중치를 올렸어요.` : '만족도가 높은 이 조합의 가중치를 올렸어요.'}
          </Text>
        </View>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  score: { fontSize: 60, lineHeight: 70, color: t.colors.primary },
  scoreUnit: { fontSize: 18, fontWeight: '700', marginBottom: 12, color: t.colors.text },
  summary: { fontSize: 15, color: t.colors.text, lineHeight: 22 },
  em: { fontWeight: '800', color: t.colors.primary },
  compare: { ...cardBase(t), flexDirection: 'row', marginTop: 18 },
  cmpCol: { flex: 1, alignItems: 'center', paddingVertical: 14, gap: 6 },
  cmpLabel: { fontSize: 12, color: t.colors.textSub, fontWeight: '600' },
  cmpValue: { fontSize: 22 },
  cmpSub: { fontSize: 11, color: t.colors.textSub },
  memo: { color: t.colors.textSub, fontSize: 13, marginTop: 10, fontStyle: 'italic' },
  section: { fontSize: 17, fontWeight: '800', color: t.colors.text, marginTop: 28, marginBottom: 12 },
  axis: { width: 92, fontSize: 13, color: t.colors.text },
  track: { flex: 1, height: 8, backgroundColor: t.colors.surfaceAlt, borderRadius: 4, overflow: 'hidden' },
  fill: { height: 8, borderRadius: 4 },
  axisVal: { width: 30, textAlign: 'right', fontWeight: '800' },
  rx: { ...cardBase(t), padding: 14, marginBottom: 10 },
  rxHead: { fontSize: 13, fontWeight: '800', color: t.colors.text },
  rxText: { flex: 1, fontSize: 14, color: t.colors.text, lineHeight: 20 },
}));
