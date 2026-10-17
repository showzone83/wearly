import { router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ItemThumb } from '@/components/ItemThumb';
import { Header, Kicker, notify, Row, Screen, Serif } from '@/components/ui';
import type { Category, ColorKey, StyleTag, WardrobeItem } from '@/data/types';
import { activeCodes, categoryLabel, categorySlot, colorLabel, colorMeta, styleLabel, withCurrent } from '@/lib/codes';
import { dayContext } from '@/lib/context';
import { diffDays, todayKey } from '@/lib/date';
import { josa } from '@/lib/josa';
import { recommend } from '@/lib/recommend';
import { useCodesVersion } from '@/store/useCodes';
import { engineInput, useStore } from '@/store/useStore';
import { colors } from '@/theme';

const SLEEP_DAYS = 30;
/** 스타일 구성비 막대 색 (많은 순) */
const STACK_COLORS = [colors.ink, colors.green, colors.beige, colors.faint, colors.line, colors.sub, colors.tile];

function isSleeping(i: WardrobeItem, today: string) {
  return !i.lastWornAt || diffDays(today, i.lastWornAt) >= SLEEP_DAYS;
}

// 목업 ⑥ 옷장 스타일 분석 (p.26): 분석 → 평가 → 추천 → 제안
export default function Analysis() {
  const items = useStore((s) => s.items);
  const looks = useStore((s) => s.looks);
  const profile = useStore((s) => s.profile);
  const setDraft = useStore((s) => s.setDraft);
  useCodesVersion(); // 코드가 갱신되면 다시 그린다
  const today = todayKey();

  if (items.length === 0) {
    return (
      <Screen header={<Header title="옷장 스타일 분석" />}>
        <Text style={{ color: colors.sub, marginTop: 40, textAlign: 'center' }}>옷을 등록하면 옷장 분석을 보여드려요.</Text>
      </Screen>
    );
  }

  // 활성 스타일 + 옷장에 있는 비활성(또는 모르는) 스타일
  const styleCodes = withCurrent(activeCodes('STYLE'), [...new Set(items.flatMap((i) => i.styles))]);
  const styleCount = styleCodes.map((t) => [t, items.filter((i) => i.styles.includes(t)).length] as [StyleTag, number]).filter(([, n]) => n > 0);
  const styleTotal = styleCount.reduce((s, [, n]) => s + n, 0);
  styleCount.sort((a, b) => b[1] - a[1]);
  const colorCount = Object.entries(
    items.reduce<Record<string, number>>((acc, i) => ({ ...acc, [i.color]: (acc[i.color] ?? 0) + 1 }), {}),
  ).sort((a, b) => b[1] - a[1]) as [ColorKey, number][];
  // 코디에 들어가는 종류(CATEGORY attrs.slot이 있는 것). 원피스는 한 벌이라도 있을 때만 보여 준다
  const catCount = withCurrent(activeCodes('CATEGORY'), [...new Set(items.map((i) => i.category))])
    .filter((c) => categorySlot(c) !== null)
    .map((c) => [c, items.filter((i) => i.category === c).length] as [Category, number])
    .filter(([c, n]) => n > 0 || categorySlot(c) !== 'DRESS');
  const sleeping = items.filter((i) => isSleeping(i, today));
  const usage = Math.round(((items.length - sleeping.length) / items.length) * 100);
  const most = [...items].sort((a, b) => b.wornCount - a.wornCount).slice(0, 3);
  const thin = catCount.filter(([c, n]) => (c === 'OUTER' || c === 'BOTTOM') && n < 3).map(([c]) => categoryLabel(c));
  const rich = catCount.length ? catCount.reduce((a, b) => (b[1] > a[1] ? b : a))[0] : items[0].category;
  const mainColor = colorCount[0][0];
  const type = styleCount[0] ? `${colorMeta(mainColor).neutral ? '뉴트럴' : colorLabel(mainColor)} ${styleLabel(styleCount[0][0])}` : '분석 중';

  const recent = looks.filter((l) => diffDays(today, l.date) <= 7);
  const avg = recent.length ? Math.round(recent.reduce((s, l) => s + l.diagnosis.aiScore, 0) / recent.length) : null;

  const reviveWith = (item: WardrobeItem) => {
    const ctx = { ...dayContext(today, profile), anchorItemId: item.id, excludeRecent: false };
    const outfits = recommend(engineInput(ctx), 3);
    if (outfits.length === 0) {
      notify('코디를 만들 수 없어요', '세탁 중인 옷을 확인하거나 옷을 더 등록해 보세요.');
      return;
    }
    setDraft({ mode: 'TODAY', outfits });
    router.push('/result');
  };

  return (
    <Screen header={<Header title="옷장 스타일 분석" />}>
      <Kicker>01 · ANALYSIS 분석</Kicker>
      <Text style={s.h}>스타일 구성비</Text>
      <View style={s.stack}>
        {styleCount.map(([t, n], i) => (
          <View key={t} style={{ flex: n, backgroundColor: STACK_COLORS[i % STACK_COLORS.length] }} />
        ))}
      </View>
      <Row style={{ flexWrap: 'wrap', gap: 12, marginTop: 8 }}>
        {styleCount.map(([t, n]) => (
          <Text key={t} style={s.legend}>
            {styleLabel(t)} {Math.round((n / styleTotal) * 100)}%
          </Text>
        ))}
      </Row>

      <Text style={s.h}>컬러 팔레트</Text>
      <Row style={{ gap: 8, flexWrap: 'wrap' }}>
        {colorCount.map(([c, n]) => (
          <View key={c} style={{ alignItems: 'center', gap: 4 }}>
            <View style={[s.swatch, { backgroundColor: colorMeta(c).hex }]} />
            <Text style={s.legend}>
              {colorLabel(c)} {n}
            </Text>
          </View>
        ))}
      </Row>

      <Text style={s.h}>카테고리 밸런스</Text>
      {catCount.map(([c, n]) => (
        <Row key={c} style={{ gap: 10, marginBottom: 6 }}>
          <Text style={s.cat}>{categoryLabel(c)}</Text>
          <View style={s.track}>
            <View style={[s.fill, { width: `${Math.min(100, n * 14)}%` }]} />
          </View>
          <Text style={s.catN}>{n}</Text>
        </Row>
      ))}

      <View style={{ height: 24 }} />
      <Kicker>02 · EVALUATION 평가</Kicker>
      <View style={s.card}>
        <Text style={s.cardSub}>나의 스타일 유형</Text>
        <Serif style={{ fontSize: 22, marginTop: 4 }}>{type}</Serif>
        <Text style={s.body}>
          {josa(categoryLabel(rich), '은', '는')} 풍부{thin.length ? `하고, ${josa(thin.join('·'), '이', '가')} 부족해요` : '하고 균형이 좋아요'}. 옷장 활용률은 최근 30일 기준 <Text style={{ fontWeight: '800' }}>{usage}%</Text>예요.
        </Text>
        {avg !== null && <Text style={s.body}>최근 7일 착용 기록 {recent.length}건 · 평균 AI 점수 {avg}점</Text>}
        <Text style={s.body}>자주 입는 옷: {most.map((m) => `${m.name}(${m.wornCount}회)`).join(', ')}</Text>
      </View>

      <Kicker>03 · RECOMMEND 잠자는 옷 살리기</Kicker>
      <Text style={[s.cardSub, { marginTop: 8 }]}>{SLEEP_DAYS}일 이상 안 입은 옷 {sleeping.length}벌 · 눌러서 이 옷이 중심인 코디를 받아 보세요</Text>
      <Row style={{ gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
        {sleeping.map((i) => (
          <Pressable key={i.id} onPress={() => reviveWith(i)} style={{ width: 96 }}>
            <ItemThumb item={i} size={96} />
            <Text style={s.legend} numberOfLines={1}>
              {i.name}
            </Text>
            <Text style={s.revive}>코디 받기 ›</Text>
          </Pressable>
        ))}
      </Row>

      <View style={{ height: 24 }} />
      <Kicker>04 · SUGGEST 제안</Kicker>
      <View style={s.card}>
        <Text style={s.body}>→ 잠자는 옷 {sleeping.length}벌부터 이번 주에 한 번씩 입어 보기</Text>
        {thin.length > 0 && <Text style={s.body}>→ {thin[0]} 1벌을 추가하면 만들 수 있는 코디가 늘어나요 (구매 전 미리보기 연동 예정)</Text>}
        <Text style={s.body}>→ 1년 넘게 안 입은 옷은 정리 후보 · 기부·리폼 안내 예정</Text>
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  h: { fontSize: 16, fontWeight: '800', color: colors.ink, marginTop: 16, marginBottom: 10 },
  stack: { flexDirection: 'row', height: 14 },
  legend: { fontSize: 12, color: colors.sub },
  swatch: { width: 36, height: 36, borderWidth: 1, borderColor: colors.line },
  cat: { width: 52, fontSize: 13, color: colors.ink },
  track: { flex: 1, height: 8, backgroundColor: colors.tile },
  fill: { height: 8, backgroundColor: colors.ink },
  catN: { width: 24, textAlign: 'right', fontWeight: '700' },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 16, marginTop: 10, marginBottom: 26, gap: 6 },
  cardSub: { fontSize: 12, color: colors.sub },
  body: { fontSize: 14, color: colors.ink, lineHeight: 21 },
  revive: { fontSize: 12, color: colors.green, fontWeight: '700' },
});
