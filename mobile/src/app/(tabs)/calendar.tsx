import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { OutfitMini } from '@/components/OutfitMini';
import { Badge, Button, confirm, PageTitle, Row, Screen, SectionHead, Stars, Title } from '@/components/ui';
import { OCCASION_LABEL, WEEKDAY_SHORT } from '@/data/constants';
import type { PlannedEvent } from '@/data/types';
import { styleLabel } from '@/lib/codes';
import { addDays, diffDays, fromKey, shortLabel, todayKey, weekStart, weekTitle } from '@/lib/date';
import { myPlace, recheckPlans } from '@/lib/placeWeather';
import { planSource, samePlace, SOURCE_LABEL } from '@/lib/planWeather';
import { upcomingPlans, useStore } from '@/store/useStore';
import { useWeatherStore } from '@/store/useWeatherStore';
import { cardBase, makeStyles, useColors } from '@/theme';

/** 예정으로 PLAN 모드 열기 (저장된 조건·장소를 채우고, 저장하면 그 예정을 바꾼다) */
const openPlan = (p: PlannedEvent) => router.push(`/coordi?mode=plan&planId=${encodeURIComponent(p.id)}&t=${Date.now()}`);

/** 내 지역이 아닌 장소 이름 (내 지역이면 null) */
const otherPlaceLabel = (p: PlannedEvent, regionCode: string): string | null =>
  p.place && !samePlace(p.place, myPlace(regionCode)) ? p.place.label : null;

/** "예보가 나왔어요 · 다시 추천" 배지 (doc/19) */
const ForecastReadyBadge = ({ plan }: { plan: PlannedEvent }) => {
  const s = useStyles();
  const colors = useColors();

  return (
    <Pressable onPress={() => openPlan(plan)} style={s.readyBadge} accessibilityRole="button" hitSlop={4}>
      <Icon name="refresh" size={13} color={colors.text} />
      <Text style={s.readyText}>예보가 나왔어요 · 다시 추천</Text>
    </Pressable>
  );
};

// 목업 ④ 코디 캘린더 (p.42): 주간 보기 · D-day · 지난 기록
export default function Calendar() {
  const dayOutfits = useStore((s) => s.dayOutfits);
  const looks = useStore((s) => s.looks);
  const plans = useStore((s) => s.plans);
  const deletePlan = useStore((s) => s.deletePlan);
  const regionCode = useWeatherStore((s) => s.regionCode);
  const s = useStyles();
  const colors = useColors();
  const today = todayKey();
  const [monday, setMonday] = useState(weekStart(today));
  const [selected, setSelected] = useState(today);

  const week = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
  const look = looks.find((l) => l.date === selected);
  const saved = dayOutfits[selected];
  const plan = plans.find((p) => p.date === selected);
  const past = selected <= today;
  const upcoming = upcomingPlans(plans);
  const history = [...looks].sort((a, b) => b.date.localeCompare(a.date));

  // 캘린더에 들어올 때마다 평년값 예정의 예보가 나왔는지 확인한다 (하루 한 번, doc/19)
  useFocusEffect(
    useCallback(() => {
      void recheckPlans();
    }, []),
  );

  return (
    <Screen
      header={
        <PageTitle
          title="코디 기록"
          sub="저장한 코디 · 착용 기록 · 다가오는 예정"
          right={
            <Pressable onPress={() => router.push('/coordi?mode=plan')} hitSlop={8} style={s.addBtn}>
              <Icon name="plus" size={14} color={colors.primary} strokeWidth={2} />
              <Text style={s.add}>예정일</Text>
            </Pressable>
          }
        />
      }
    >
      <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
        <Title style={s.week}>{weekTitle(monday)}</Title>
        <Row style={{ gap: 14 }}>
          <Pressable onPress={() => setMonday(addDays(monday, -7))} hitSlop={8} accessibilityLabel="이전 주">
            <Icon name="back" size={20} />
          </Pressable>
          <Pressable onPress={() => setMonday(addDays(monday, 7))} hitSlop={8} accessibilityLabel="다음 주">
            <Icon name="chevron" size={20} />
          </Pressable>
        </Row>
      </Row>

      <View style={[s.strip, s.stripCard]}>
        {week.map((d) => {
          const on = d === selected;
          const has = !!dayOutfits[d] || looks.some((l) => l.date === d) || plans.some((p) => p.date === d);
          const ready = plans.some((p) => p.date === d && p.forecastReady);
          return (
            <Pressable key={d} onPress={() => setSelected(d)} style={[s.day, on && s.dayOn]}>
              <Text style={[s.dayW, { color: on ? colors.onPrimary : colors.textSub }]}>{WEEKDAY_SHORT[fromKey(d).getDay()]}</Text>
              <Title style={[s.dayN, { color: on ? colors.onPrimary : colors.text }]}>{fromKey(d).getDate()}</Title>
              <View
                style={[
                  s.dot,
                  { backgroundColor: has ? (on ? colors.onPrimary : d === today ? colors.accent : colors.primary) : 'transparent' },
                  ready && s.dotReady,
                  ready && !on && { backgroundColor: colors.accent },
                ]}
              />
            </Pressable>
          );
        })}
      </View>

      <Text style={s.section}>
        {selected === today ? '오늘' : shortLabel(selected)} {selected === today && `· ${shortLabel(selected)}`}
      </Text>
      <View style={s.card}>
        {look ? (
          <Pressable style={s.cardRow} onPress={() => router.push(`/look/${look.id}`)}>
            <OutfitMini items={look.items} />
            <View style={{ flex: 1 }}>
              <Text style={s.cardTitle}>{OCCASION_LABEL[look.occasion]} 코디</Text>
              <Text style={s.cardSub}>AI {look.diagnosis.aiScore}점 · 기록 완료</Text>
              <Stars value={look.selfScore} size={14} />
            </View>
            <Icon name="chevron" size={18} color={colors.textMuted} />
          </Pressable>
        ) : saved ? (
          <View style={s.cardRow}>
            <OutfitMini items={saved.outfit.items} />
            <View style={{ flex: 1 }}>
              <Text style={s.cardTitle}>{OCCASION_LABEL[saved.outfit.context.occasion]} 코디</Text>
              <Text style={s.cardSub}>저장됨 · {past ? '기록 대기' : '그날 아침 오늘 코디로'}</Text>
              {past && (
                <Pressable onPress={() => router.push(`/look/new?date=${selected}`)}>
                  <Text style={s.link}>착용 기록 ›</Text>
                </Pressable>
              )}
            </View>
          </View>
        ) : (
          <View style={{ gap: 10 }}>
            <Text style={s.cardSub}>{past ? '이날 기록이 없어요.' : '아직 저장한 코디가 없어요.'}</Text>
            {past ? (
              <Button title="착용 기록 남기기" variant="outline" onPress={() => router.push(`/look/new?date=${selected}`)} style={{ height: 42 }} />
            ) : diffDays(selected, today) <= 2 ? (
              <Button title="홈에서 추천 보기" variant="outline" onPress={() => router.navigate('/home')} style={{ height: 42 }} />
            ) : null}
          </View>
        )}
        {plan && (
          <View style={{ marginTop: 10, gap: 6 }}>
            <Text style={[s.cardSub, { color: colors.primary }]}>
              {[
                `예정 · ${plan.title}`,
                otherPlaceLabel(plan, regionCode),
                `${plan.weather.temp}°C (${SOURCE_LABEL[planSource(plan)]})`,
              ]
                .filter(Boolean)
                .join(' · ')}{' '}
              {plan.outfit ? '(코디 저장됨)' : '(추천 대기)'}
            </Text>
            {plan.forecastReady && <ForecastReadyBadge plan={plan} />}
          </View>
        )}
      </View>

      <SectionHead title="다가오는 예정" />
      {upcoming.length === 0 && <Text style={s.cardSub}>예정일 코디가 없어요. 오른쪽 위 ‘+ 예정일’로 추가해 보세요.</Text>}
      {upcoming.map((p) => {
        const dday = diffDays(p.date, today);
        return (
          <View key={p.id} style={[s.plan, { borderLeftColor: p.outfit ? colors.primary : colors.accent }]}>
            <View style={s.dday}>
              <Title style={[s.ddayN, { color: p.outfit ? colors.primary : colors.text }]}>{dday === 0 ? 'D-DAY' : `D-${dday}`}</Title>
              <Text style={s.ddayDate}>{shortLabel(p.date)}</Text>
            </View>
            <View style={{ flex: 1, gap: 4 }}>
              <Text style={s.cardTitle}>{p.title}</Text>
              {otherPlaceLabel(p, regionCode) && (
                <Row style={{ gap: 4 }}>
                  <Icon name="pin" size={13} color={colors.textSub} />
                  <Text style={s.cardSub}>{otherPlaceLabel(p, regionCode)}</Text>
                </Row>
              )}
              <Text style={s.cardSub}>
                {[...p.concepts.map(styleLabel), `${p.weather.temp}°C (${SOURCE_LABEL[planSource(p)]})`].join(' · ')}
              </Text>
              {p.forecastReady && <ForecastReadyBadge plan={p} />}
              {p.outfit ? <OutfitMini items={p.outfit.items} size={38} /> : <Text style={s.cardSub}>추천 대기</Text>}
              <Row style={{ gap: 12, marginTop: 4 }}>
                {!p.outfit && (
                  <Pressable onPress={() => openPlan(p)}>
                    <Text style={s.link}>추천 받기 ›</Text>
                  </Pressable>
                )}
                <Pressable onPress={() => confirm('예정을 삭제할까요?', p.title, () => deletePlan(p.id), '삭제')}>
                  <Text style={[s.link, { color: colors.textMuted }]}>삭제</Text>
                </Pressable>
              </Row>
            </View>
            {p.outfit && <Badge>D-1 알림</Badge>}
          </View>
        );
      })}

      <SectionHead title="지난 기록" />
      {history.length === 0 && <Text style={s.cardSub}>착용 기록을 남기면 AI 진단과 함께 여기에 쌓여요.</Text>}
      {history.length > 0 && (
        <View style={s.histCard}>
          {history.map((l, i) => (
            <Pressable key={l.id} style={[s.hist, i === history.length - 1 && { borderBottomWidth: 0 }]} onPress={() => router.push(`/look/${l.id}`)}>
              <Text style={s.histDate}>{shortLabel(l.date)}</Text>
              <Text style={s.histScore}>AI {l.diagnosis.aiScore}점</Text>
              <Stars value={l.selfScore} size={15} />
              <Icon name="chevron" size={16} color={colors.textMuted} />
            </Pressable>
          ))}
        </View>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  addBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    borderWidth: 1,
    borderColor: t.colors.primary,
    borderRadius: t.radius.pill,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  add: { color: t.colors.primary, fontWeight: '800', fontSize: 13 },
  week: { fontSize: 20 },
  strip: { flexDirection: 'row', marginTop: 14, gap: 4 },
  stripCard: { ...cardBase(t), padding: 8 },
  day: { flex: 1, alignItems: 'center', paddingVertical: 8, gap: 4, borderRadius: t.radius.md },
  dayOn: { backgroundColor: t.colors.primary },
  dayW: { fontSize: 12, fontWeight: '700' },
  dayN: { fontSize: 18 },
  dot: { width: 5, height: 5, borderRadius: 3 },
  dotReady: { width: 12 },
  readyBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: 4,
    backgroundColor: t.colors.accentSoft,
    borderRadius: t.radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  readyText: { fontSize: 12, fontWeight: '800', color: t.colors.text },
  section: { fontSize: 17, fontWeight: '800', color: t.colors.text, marginTop: 26, marginBottom: 12 },
  card: { ...cardBase(t), padding: 16 },
  cardRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  cardTitle: { fontSize: 16, fontWeight: '800', color: t.colors.text },
  cardSub: { fontSize: 13, color: t.colors.textSub, marginVertical: 2 },
  link: { color: t.colors.primary, fontWeight: '800', marginTop: 4 },
  plan: {
    ...cardBase(t),
    flexDirection: 'row',
    gap: 14,
    borderLeftWidth: 4,
    padding: 14,
    marginBottom: 10,
    alignItems: 'flex-start',
  },
  dday: { width: 64, alignItems: 'center' },
  ddayN: { fontSize: 20 },
  ddayDate: { fontSize: 11, color: t.colors.textSub, marginTop: 2 },
  histCard: { ...cardBase(t), paddingHorizontal: 16 },
  hist: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
    gap: 12,
  },
  histDate: { fontSize: 15, width: 92, fontWeight: '700', color: t.colors.text },
  histScore: { flex: 1, color: t.colors.textSub },
}));
