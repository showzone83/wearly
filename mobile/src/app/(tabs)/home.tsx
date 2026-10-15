import { router } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ChipGroup } from '@/components/Chip';
import { condIcon, Icon } from '@/components/Icon';
import { OutfitBoard, OutfitItemRows, ReasonBox } from '@/components/OutfitView';
import { Badge, Button, Card, notify, Row, Screen, Serif } from '@/components/ui';
import { DISLIKE_REASONS, OCCASION_LABEL, WEEKDAY_SHORT } from '@/data/constants';
import type { Outfit, OutfitItems, Slot } from '@/data/types';
import { dayContext, isWorkDay } from '@/lib/context';
import { addDays, fromKey, longLabel, todayKey } from '@/lib/date';
import { canRecommend, recommend } from '@/lib/recommend';
import { COND_LABEL, forecast, weatherNotice } from '@/lib/weather';
import { engineInput, useStore } from '@/store/useStore';
import { colors } from '@/theme';

const DAY_NAMES = ['오늘', '내일', '모레'];

export default function Home() {
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const affinity = useStore((s) => s.affinity);
  const dayOutfits = useStore((s) => s.dayOutfits);
  const looks = useStore((s) => s.looks);
  const plans = useStore((s) => s.plans);
  const saveDayOutfit = useStore((s) => s.saveDayOutfit);
  const setFeedback = useStore((s) => s.setFeedback);

  const today = todayKey();
  const [offset, setOffset] = useState(0);
  const date = addDays(today, offset);

  const [variant, setVariant] = useState(0);
  const [fixed, setFixed] = useState<OutfitItems>({});
  const [pinned, setPinned] = useState<Slot[]>([]);
  const [override, setOverride] = useState<Outfit | null>(null);
  const [dislikeOpen, setDislikeOpen] = useState(false);
  const [dislikeReasons, setDislikeReasons] = useState<string[]>([]);

  useEffect(() => {
    setVariant(0);
    setFixed({});
    setPinned([]);
    setOverride(null);
    setDislikeOpen(false);
  }, [date]);

  const ctx = useMemo(() => dayContext(date, profile), [date, profile]);
  // items·affinity 등이 바뀌면 engineInput이 스토어에서 새 값을 읽도록 의존성에 둔다
  const input = useMemo(() => engineInput({ ...ctx, fixed }), [ctx, fixed, items, affinity, dayOutfits, looks]);
  const candidates = useMemo(() => recommend(input, 3), [input]);
  const saved = dayOutfits[date];
  const current: Outfit | undefined =
    override ?? (variant === 0 && saved ? saved.outfit : candidates[variant % Math.max(candidates.length, 1)]);
  const isSaved = !!saved && current?.id === saved.outfit.id && !override;

  const weather = forecast(date);
  const notice = weatherNotice(weather);
  const work = isWorkDay(date, profile);
  const plan = plans.find((p) => p.date === date);
  const ready = canRecommend(items);
  const recorded = looks.some((l) => l.date === date);

  const shuffle = () => {
    if (!current) return;
    const nextFixed: OutfitItems = {};
    for (const k of pinned) nextFixed[k] = current.items[k];
    setFixed(nextFixed);
    setOverride(null);
    setVariant((v) => v + 1);
  };

  return (
    <Screen
      header={
        <Row style={s.top}>
          <Serif style={s.logo}>WEARLY</Serif>
          <Pressable onPress={() => notify('알림', '아침 7:30 코디 푸시는 앱 출시 버전에서 동작해요.')} hitSlop={10}>
            <Icon name="bell" />
          </Pressable>
        </Row>
      }
    >
      {/* 3일 탭 (p.21) */}
      <View style={s.days}>
        {DAY_NAMES.map((name, i) => {
          const key = addDays(today, i);
          const w = forecast(key);
          const on = offset === i;
          const d = fromKey(key);
          return (
            <Pressable key={key} onPress={() => setOffset(i)} style={[s.day, on ? s.dayOn : s.dayOff]} accessibilityState={{ selected: on }}>
              <Text style={[s.dayName, { color: on ? colors.beige : colors.sub }]}>{name}</Text>
              <Serif style={[s.dayNum, { color: on ? '#fff' : colors.ink }]}>
                {WEEKDAY_SHORT[d.getDay()]} {d.getDate()}
              </Serif>
              <Row style={{ gap: 3 }}>
                <Icon name={condIcon(w.cond)} size={13} color={on ? colors.beige : colors.sub} />
                <Text style={[s.dayTemp, { color: on ? '#fff' : colors.ink }]}>{w.temp}°</Text>
              </Row>
              <Text style={[s.dayWork, { color: on ? '#C9CCD1' : colors.sub }]}>{isWorkDay(key, profile) ? '근무' : '휴무'}</Text>
            </Pressable>
          );
        })}
      </View>

      <Text style={s.dateLine}>{longLabel(date)} · 서울</Text>
      <Row style={{ gap: 8, marginTop: 8 }}>
        <Badge tone={work ? 'outline' : 'beige'}>{work ? '근무일' : '휴무일'}</Badge>
        {offset > 0 && <Badge tone="green">예보 기반 미리보기</Badge>}
        <Text style={s.badgeNote}>{ctx.occasion === 'COMMUTE' ? '출근 코디로 추천했어요' : '편한 데일리 코디로 추천했어요'}</Text>
      </Row>
      <Serif style={s.headline}>
        {DAY_NAMES[offset]} 뭐 입지?{'\n'}
        <Text style={{ color: colors.green }}>{offset === 0 ? '내 옷으로 완성했어요.' : '미리 준비해 두세요.'}</Text>
      </Serif>

      {/* 날씨 카드 */}
      <View style={s.weather}>
        <Icon name={condIcon(weather.cond)} size={40} color={colors.beige} filled />
        <View style={{ flex: 1 }}>
          <Row style={{ gap: 8, alignItems: 'flex-end' }}>
            <Serif style={s.temp}>{weather.temp}°</Serif>
            <Text style={s.cond}>{COND_LABEL[weather.cond]}</Text>
          </Row>
          <Text style={s.weatherSub}>
            최저 {weather.min}° / 최고 {weather.max}° · 강수확률 {weather.rainProb}%
          </Text>
        </View>
        <View style={s.occBadge}>
          <Text style={s.occText}>{OCCASION_LABEL[ctx.occasion]}</Text>
        </View>
      </View>
      {notice && (
        <View style={s.notice}>
          <Text style={s.noticeText}>{notice}</Text>
        </View>
      )}
      {plan && (
        <Pressable onPress={() => router.push('/calendar')} style={s.plan}>
          <Text style={s.planText}>
            예정 · <Text style={{ fontWeight: '800' }}>{plan.title}</Text> {plan.outfit ? '— 저장한 코디가 있어요' : '— 아직 추천 전이에요'}
          </Text>
          <Icon name="chevron" size={16} color={colors.green} />
        </Pressable>
      )}

      {!ready.ok ? (
        <Card style={{ marginTop: 26, alignItems: 'center', paddingVertical: 28 }}>
          <Serif style={{ fontSize: 20, textAlign: 'center' }}>새 옷을 사기 전에,{'\n'}내 옷장부터.</Serif>
          <Text style={s.emptySub}>첫 코디까지 {ready.missing.join(', ')} 1벌씩 남았어요.</Text>
          <Button title="옷 등록하기" onPress={() => router.push('/item/new')} style={{ marginTop: 18, alignSelf: 'stretch' }} />
        </Card>
      ) : current ? (
        <>
          <Row style={s.sectionHead}>
            <Text style={s.section}>{DAY_NAMES[offset]}의 추천 코디</Text>
            <Pressable onPress={shuffle} hitSlop={8}>
              <Text style={s.link}>전체 바꾸기</Text>
            </Pressable>
          </Row>
          {pinned.length > 0 && <Text style={s.pinNote}>고정한 아이템은 전체 바꾸기에서 유지돼요</Text>}
          <OutfitBoard outfit={current} />
          <ReasonBox reasons={current.reasons} />
          <View style={{ marginTop: 8 }}>
            <OutfitItemRows
              outfit={current}
              input={input}
              onChange={setOverride}
              pinned={pinned}
              onTogglePin={(k) => setPinned((p) => (p.includes(k) ? p.filter((x) => x !== k) : [...p, k]))}
            />
          </View>

          {isSaved ? (
            <View style={{ marginTop: 18, gap: 12 }}>
              <Row style={{ gap: 6 }}>
                <Icon name="check" size={18} color={colors.green} />
                <Text style={{ color: colors.green, fontWeight: '700' }}>
                  {offset === 0 ? '오늘 코디로 저장했어요' : `${DAY_NAMES[offset]} 코디로 저장했어요 · 그날 아침 ‘오늘 코디’로 보여요`}
                </Text>
              </Row>
              {saved.feedback ? (
                <Text style={s.feedbackDone}>
                  피드백 고마워요 — {saved.feedback === 'LIKE' ? '비슷한 조합을 더 추천할게요.' : '다음 추천에서 덜 보여드릴게요.'}
                </Text>
              ) : (
                <Row style={{ gap: 8 }}>
                  <Button title="좋아요" variant="outline" onPress={() => setFeedback(date, 'LIKE')} style={{ flex: 1, height: 44 }} />
                  <Button title="별로예요" variant="outline" onPress={() => setDislikeOpen(true)} style={{ flex: 1, height: 44 }} />
                </Row>
              )}
              {dislikeOpen && !saved.feedback && (
                <Card>
                  <Text style={{ fontWeight: '700', marginBottom: 10 }}>어떤 점이 아쉬웠나요?</Text>
                  <ChipGroup options={DISLIKE_REASONS} labels={(r) => r} value={dislikeReasons} multi small onChange={setDislikeReasons} />
                  <Button
                    title="보내기"
                    style={{ marginTop: 14, height: 44 }}
                    onPress={() => {
                      setFeedback(date, 'DISLIKE', dislikeReasons);
                      setDislikeOpen(false);
                    }}
                  />
                </Card>
              )}
              {offset === 0 && (
                <Button
                  title={recorded ? '오늘 기록·진단 보기' : '착용 기록하고 AI 진단 받기'}
                  variant="green"
                  onPress={() => {
                    const look = looks.find((l) => l.date === date);
                    router.push(look ? `/look/${look.id}` : `/look/new?date=${date}`);
                  }}
                />
              )}
            </View>
          ) : (
            <Button
              title={saved ? '이 코디로 바꿔 저장' : `${DAY_NAMES[offset]} 코디로 저장`}
              onPress={() => {
                saveDayOutfit(date, current);
                setOverride(null);
                setVariant(0);
              }}
              style={{ marginTop: 20 }}
            />
          )}
        </>
      ) : (
        <Card style={{ marginTop: 26 }}>
          <Text style={s.emptySub}>조건에 맞는 코디를 만들 옷이 부족해요. 세탁 중인 옷을 확인해 보세요.</Text>
        </Card>
      )}
    </Screen>
  );
}

const s = StyleSheet.create({
  top: { justifyContent: 'space-between', paddingHorizontal: 20, height: 52 },
  logo: { fontSize: 20, letterSpacing: 4 },
  days: { flexDirection: 'row', gap: 8, marginTop: 4 },
  day: { flex: 1, paddingVertical: 10, alignItems: 'center', gap: 3, borderWidth: 1 },
  dayOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  dayOff: { backgroundColor: colors.surface, borderColor: colors.line },
  dayName: { fontSize: 12, fontWeight: '700' },
  dayNum: { fontSize: 17 },
  dayTemp: { fontSize: 12, fontWeight: '600' },
  dayWork: { fontSize: 11 },
  dateLine: { marginTop: 18, color: colors.sub, fontSize: 13, fontWeight: '600' },
  badgeNote: { color: colors.sub, fontSize: 12 },
  headline: { fontSize: 27, lineHeight: 38, marginTop: 10 },
  weather: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: colors.ink, padding: 16, marginTop: 18 },
  temp: { color: '#fff', fontSize: 30 },
  cond: { color: '#fff', fontSize: 14, marginBottom: 6 },
  weatherSub: { color: '#C9CCD1', fontSize: 12, marginTop: 2 },
  occBadge: { borderWidth: 1, borderColor: colors.beige, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 4 },
  occText: { color: colors.beige, fontSize: 12, fontWeight: '700' },
  notice: { backgroundColor: colors.greenSoft, padding: 12, marginTop: 8 },
  noticeText: { color: colors.green, fontSize: 13, lineHeight: 19 },
  plan: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderColor: colors.green, padding: 12, marginTop: 8 },
  planText: { color: colors.green, fontSize: 13, flex: 1 },
  sectionHead: { justifyContent: 'space-between', marginTop: 28, marginBottom: 12 },
  section: { fontSize: 18, fontWeight: '800', color: colors.ink },
  link: { color: colors.green, fontWeight: '700' },
  pinNote: { color: colors.sub, fontSize: 12, marginTop: -6, marginBottom: 10 },
  emptySub: { color: colors.sub, fontSize: 14, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  feedbackDone: { color: colors.sub, fontSize: 13 },
});
