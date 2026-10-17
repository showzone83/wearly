import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { OutfitMini } from '@/components/OutfitMini';
import { Badge, Button, confirm, Row, Screen, Serif, Stars } from '@/components/ui';
import { OCCASION_LABEL, WEEKDAY_SHORT } from '@/data/constants';
import { styleLabel } from '@/lib/codes';
import { addDays, diffDays, fromKey, shortLabel, todayKey, weekStart, weekTitle } from '@/lib/date';
import { upcomingPlans, useStore } from '@/store/useStore';
import { colors } from '@/theme';

// 목업 ④ 코디 캘린더 (p.42): 주간 보기 · D-day · 지난 기록
export default function Calendar() {
  const dayOutfits = useStore((s) => s.dayOutfits);
  const looks = useStore((s) => s.looks);
  const plans = useStore((s) => s.plans);
  const deletePlan = useStore((s) => s.deletePlan);
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

  return (
    <Screen
      header={
        <Row style={s.top}>
          <Text style={s.title}>코디 캘린더</Text>
          <Pressable onPress={() => router.push('/coordi?mode=plan')} hitSlop={8}>
            <Text style={s.add}>+ 예정일</Text>
          </Pressable>
        </Row>
      }
    >
      <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
        <Serif style={s.week}>{weekTitle(monday)}</Serif>
        <Row style={{ gap: 14 }}>
          <Pressable onPress={() => setMonday(addDays(monday, -7))} hitSlop={8} accessibilityLabel="이전 주">
            <Icon name="back" size={20} />
          </Pressable>
          <Pressable onPress={() => setMonday(addDays(monday, 7))} hitSlop={8} accessibilityLabel="다음 주">
            <Icon name="chevron" size={20} />
          </Pressable>
        </Row>
      </Row>

      <View style={s.strip}>
        {week.map((d) => {
          const on = d === selected;
          const has = !!dayOutfits[d] || looks.some((l) => l.date === d) || plans.some((p) => p.date === d);
          return (
            <Pressable key={d} onPress={() => setSelected(d)} style={[s.day, on && s.dayOn]}>
              <Text style={[s.dayW, { color: on ? colors.beige : colors.sub }]}>{WEEKDAY_SHORT[fromKey(d).getDay()]}</Text>
              <Serif style={[s.dayN, { color: on ? '#fff' : colors.ink }]}>{fromKey(d).getDate()}</Serif>
              <View style={[s.dot, { backgroundColor: has ? (d === today ? colors.beige : colors.green) : 'transparent' }]} />
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
            <Icon name="chevron" size={18} color={colors.faint} />
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
          <Text style={[s.cardSub, { marginTop: 10, color: colors.green }]}>
            예정 · {plan.title} {plan.outfit ? '(코디 저장됨)' : '(추천 대기)'}
          </Text>
        )}
      </View>

      <Text style={s.section}>다가오는 예정</Text>
      {upcoming.length === 0 && <Text style={s.cardSub}>예정일 코디가 없어요. 오른쪽 위 ‘+ 예정일’로 추가해 보세요.</Text>}
      {upcoming.map((p) => {
        const dday = diffDays(p.date, today);
        return (
          <View key={p.id} style={[s.plan, { borderLeftColor: p.outfit ? colors.green : colors.beige }]}>
            <View style={s.dday}>
              <Serif style={[s.ddayN, { color: p.outfit ? colors.green : colors.ink }]}>{dday === 0 ? 'D-DAY' : `D-${dday}`}</Serif>
              <Text style={s.ddayDate}>{shortLabel(p.date)}</Text>
            </View>
            <View style={{ flex: 1, gap: 4 }}>
              <Text style={s.cardTitle}>{p.title}</Text>
              <Text style={s.cardSub}>
                {p.concepts.map(styleLabel).join(' · ')} · {p.weather.temp}°C{p.weather.manual ? ' (직접 입력)' : ''}
              </Text>
              {p.outfit ? <OutfitMini items={p.outfit.items} size={38} /> : <Text style={s.cardSub}>추천 대기</Text>}
              <Row style={{ gap: 12, marginTop: 4 }}>
                {!p.outfit && (
                  <Pressable onPress={() => router.push('/coordi?mode=plan')}>
                    <Text style={s.link}>추천 받기 ›</Text>
                  </Pressable>
                )}
                <Pressable onPress={() => confirm('예정을 삭제할까요?', p.title, () => deletePlan(p.id), '삭제')}>
                  <Text style={[s.link, { color: colors.faint }]}>삭제</Text>
                </Pressable>
              </Row>
            </View>
            {p.outfit && <Badge>D-1 알림</Badge>}
          </View>
        );
      })}

      <Text style={s.section}>지난 기록</Text>
      {history.length === 0 && <Text style={s.cardSub}>착용 기록을 남기면 AI 진단과 함께 여기에 쌓여요.</Text>}
      {history.map((l) => (
        <Pressable key={l.id} style={s.hist} onPress={() => router.push(`/look/${l.id}`)}>
          <Serif style={s.histDate}>{shortLabel(l.date)}</Serif>
          <Text style={s.histScore}>AI {l.diagnosis.aiScore}점</Text>
          <Stars value={l.selfScore} size={15} />
        </Pressable>
      ))}
    </Screen>
  );
}

const s = StyleSheet.create({
  top: { justifyContent: 'space-between', paddingHorizontal: 20, height: 52 },
  title: { fontSize: 18, fontWeight: '800', color: colors.ink, letterSpacing: 2 },
  add: { color: colors.green, fontWeight: '800' },
  week: { fontSize: 20 },
  strip: { flexDirection: 'row', marginTop: 16, gap: 4 },
  day: { flex: 1, alignItems: 'center', paddingVertical: 8, gap: 4 },
  dayOn: { backgroundColor: colors.ink },
  dayW: { fontSize: 12, fontWeight: '700' },
  dayN: { fontSize: 18 },
  dot: { width: 5, height: 5, borderRadius: 3 },
  section: { fontSize: 14, fontWeight: '700', color: colors.sub, marginTop: 26, marginBottom: 10 },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 16 },
  cardRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  cardTitle: { fontSize: 16, fontWeight: '800', color: colors.ink },
  cardSub: { fontSize: 13, color: colors.sub, marginVertical: 2 },
  link: { color: colors.green, fontWeight: '800', marginTop: 4 },
  plan: { flexDirection: 'row', gap: 14, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderLeftWidth: 4, padding: 14, marginBottom: 10, alignItems: 'flex-start' },
  dday: { width: 64, alignItems: 'center' },
  ddayN: { fontSize: 20 },
  ddayDate: { fontSize: 11, color: colors.sub, marginTop: 2 },
  hist: { flexDirection: 'row', alignItems: 'center', paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 14 },
  histDate: { fontSize: 15, width: 92 },
  histScore: { flex: 1, color: colors.sub },
});
