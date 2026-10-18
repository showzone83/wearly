import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { ChipGroup } from '@/components/Chip';
import { condIcon, Icon, type IconName } from '@/components/Icon';
import { Mascot } from '@/components/Mascot';
import { OutfitCard } from '@/components/OutfitMini';
import { OutfitBoard, OutfitItemRows, ReasonBox } from '@/components/OutfitView';
import { Button, Card, notify, Row, Screen, SectionHead, Title } from '@/components/ui';
import { router } from 'expo-router';

import { OCCASION_LABEL, DISLIKE_REASONS, WEEKDAY_SHORT } from '@/data/constants';
import type { Outfit, OutfitItems, Slot, Weather } from '@/data/types';
import { dayContext, isWorkDay } from '@/lib/context';
import { addDays, diffDays, fromKey, longLabel, todayKey } from '@/lib/date';
import { canRecommend, recommend } from '@/lib/recommend';
import { activeItems } from '@/lib/retire';
import { COND_LABEL, currentWeather, forecast, weatherNotice } from '@/lib/weather';
import { regionLabel } from '@/lib/weatherSync';
import { engineInput, useStore } from '@/store/useStore';
import { useWeatherStore } from '@/store/useWeatherStore';
import { cardBase, makeStyles, useColors } from '@/theme';

const DAY_NAMES = ['오늘', '내일', '모레'];

/** 날씨 카드 첫 줄 (보드: "오늘도 좋은 날씨에요!") */
const weatherHeadline = (w: Weather, dayName: string): string => {
  if (w.cond === 'RAIN') return `${dayName}은 비 소식이 있어요`;
  if (w.cond === 'CLOUDY') return `${dayName}은 구름이 조금 있어요`;
  return `${dayName}도 좋은 날씨예요!`;
};

export default function Home() {
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const affinity = useStore((s) => s.affinity);
  const dayOutfits = useStore((s) => s.dayOutfits);
  const looks = useStore((s) => s.looks);
  const plans = useStore((s) => s.plans);
  // 서버 날씨가 도착하면 예보·추천을 다시 계산한다 (lib/weather.ts가 스토어 값을 읽는다)
  const serverWeather = useWeatherStore((s) => s.weather);
  const regionCode = useWeatherStore((s) => s.regionCode);
  const saveDayOutfit = useStore((s) => s.saveDayOutfit);
  const setFeedback = useStore((s) => s.setFeedback);
  const s = useStyles();
  const c = useColors();

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

  const ctx = useMemo(() => dayContext(date, profile), [date, profile, serverWeather]);
  // items·affinity 등이 바뀌면 engineInput이 스토어에서 새 값을 읽도록 의존성에 둔다
  const input = useMemo(() => engineInput({ ...ctx, fixed }), [ctx, fixed, items, affinity, dayOutfits, looks]);
  const candidates = useMemo(() => recommend(input, 3), [input]);
  const saved = dayOutfits[date];
  const current: Outfit | undefined =
    override ?? (variant === 0 && saved ? saved.outfit : candidates[variant % Math.max(candidates.length, 1)]);
  const isSaved = !!saved && current?.id === saved.outfit.id && !override;

  const weather = forecast(date);
  const notice = weatherNotice(weather);
  const now = offset === 0 ? currentWeather() : null;
  const work = isWorkDay(date, profile);
  const plan = plans.find((p) => p.date === date);
  const ready = canRecommend(items);
  const recorded = looks.some((l) => l.date === date);
  // 통계는 사용 중인 옷만 (사용하지 않는 옷은 doc/13). 마이 탭과 같은 기준: 최근 30일 안에 입은 옷의 비율
  const inUse = activeItems(items);
  const active = inUse.filter((i) => i.lastWornAt && diffDays(today, i.lastWornAt) <= 30).length;
  const usage = inUse.length ? Math.round((active / inUse.length) * 100) : 0;

  const shuffle = () => {
    if (!current) return;
    const nextFixed: OutfitItems = {};
    for (const k of pinned) nextFixed[k] = current.items[k];
    setFixed(nextFixed);
    setOverride(null);
    setVariant((v) => v + 1);
  };

  const greetingName = profile.name ? `${profile.name}님` : '';

  return (
    <Screen>
      {/* 인사 + 마스코트 (보드 홈 맨 위) */}
      <View style={s.greet}>
        <View style={{ flex: 1, paddingTop: 18 }}>
          <Title style={s.hello}>안녕하세요{greetingName ? `,\n${greetingName}!` : '!'}</Title>
          <Text style={s.helloSub}>
            {offset === 0 ? '오늘은 어떤 스타일이' : `${DAY_NAMES[offset]}은 어떤 스타일이`}
            {'\n'}끌리세요?
          </Text>
        </View>
        <View>
          <Mascot pose="hello" size={128} />
          <Pressable
            onPress={() => notify('알림', '아침 7:30 코디 푸시는 앱 출시 버전에서 동작해요.')}
            hitSlop={10}
            style={s.bell}
            accessibilityLabel="알림"
          >
            <Icon name="bell" size={20} color={c.textSub} />
          </Pressable>
        </View>
      </View>

      {/* 3일 탭 (p.21) */}
      <View style={s.days}>
        {DAY_NAMES.map((name, i) => {
          const key = addDays(today, i);
          const w = forecast(key);
          const on = offset === i;
          const d = fromKey(key);
          return (
            <Pressable
              key={key}
              onPress={() => setOffset(i)}
              style={[s.day, on ? s.dayOn : s.dayOff]}
              accessibilityRole="button"
              accessibilityState={{ selected: on }}
            >
              <Text style={[s.dayName, { color: on ? c.onPrimary : c.text }]}>
                {name} {WEEKDAY_SHORT[d.getDay()]} {d.getDate()}
              </Text>
              <Row style={{ gap: 3 }}>
                <Icon name={condIcon(w.cond)} size={12} color={on ? c.onPrimary : c.textSub} filled={on} />
                <Text style={[s.dayTemp, { color: on ? c.onPrimary : c.textSub }]}>
                  {w.temp}° · {isWorkDay(key, profile) ? '근무' : '휴무'}
                </Text>
              </Row>
            </Pressable>
          );
        })}
      </View>

      {/* 날씨 카드 (보드: 해 아이콘 · 최고 기온 · 맑음 ›) */}
      <Pressable style={s.weather} onPress={() => router.navigate('/coordi')} accessibilityRole="button">
        <View style={s.sun}>
          <Icon
            name={condIcon(weather.cond)}
            size={34}
            color={weather.cond === 'SUNNY' ? c.accent : c.textSub}
            filled
          />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={s.weatherHead}>{weatherHeadline(weather, DAY_NAMES[offset])}</Text>
          <Text style={s.weatherSub}>
            최고 {weather.max}° <Text style={{ color: c.border }}>|</Text> {COND_LABEL[weather.cond]}
            <Text style={{ color: c.border }}> |</Text> 강수 {weather.rainProb}%
          </Text>
          {now ? (
            <Text style={s.weatherMeta}>
              지금 {now.temp}°{now.condText ? ` ${now.condText}` : ''}
              {now.stale ? ' · 업데이트 지연' : ''}
            </Text>
          ) : null}
          <Text style={s.weatherMeta}>
            {longLabel(date)} · {regionLabel(regionCode)} · {work ? '근무일' : '휴무일'} ·{' '}
            {ctx.occasion === 'COMMUTE' ? '출근 코디로 추천해요' : `${OCCASION_LABEL[ctx.occasion]} 코디로 추천해요`}
            {offset > 0 ? ' · 예보 기반 미리보기' : ''}
          </Text>
        </View>
        <Icon name="chevron" size={18} color={c.textMuted} />
      </Pressable>
      {notice && (
        <View style={s.notice}>
          <Text style={s.noticeText}>{notice}</Text>
        </View>
      )}
      {plan && (
        <Pressable onPress={() => router.push('/calendar')} style={s.plan}>
          <Icon name="calendar" size={18} color={c.primary} />
          <Text style={s.planText}>
            예정 · <Text style={{ fontWeight: '800' }}>{plan.title}</Text>{' '}
            {plan.outfit ? '— 저장한 코디가 있어요' : '— 아직 추천 전이에요'}
          </Text>
          <Icon name="chevron" size={16} color={c.primary} />
        </Pressable>
      )}

      {/* 내 옷장 통계 카드 3개 */}
      <SectionHead title="내 옷장" />
      <View style={s.stats}>
        <StatCard
          icon="hanger"
          label="등록한 옷"
          value={`${inUse.length}`}
          unit="벌"
          onPress={() => router.navigate('/closet')}
        />
        <StatCard icon="magic" label={`${DAY_NAMES[offset]} 추천 코디`} value={`${candidates.length}`} unit="개" />
        <StatCard icon="chart" label="활용도" value={`${usage}`} unit="%" onPress={() => router.push('/analysis')} />
      </View>

      {!ready.ok ? (
        <Card style={s.empty}>
          <Mascot pose="closet" size={150} sceneWidth={250} />
          <Title style={{ fontSize: 20, textAlign: 'center', marginTop: 8 }}>
            새 옷을 사기 전에,{'\n'}내 옷장부터.
          </Title>
          <Text style={s.emptySub}>
            첫 코디까지 {ready.missing.join(', ')} 1벌씩 남았어요.{'\n'}원피스 한 벌로도 시작할 수 있어요.
          </Text>
          <Button
            title="옷 등록하기"
            onPress={() => router.push('/item/new')}
            style={{ marginTop: 18, alignSelf: 'stretch' }}
          />
        </Card>
      ) : current ? (
        <>
          {/* 이런 코디는 어때요? (후보 3개를 가로 카드로) */}
          <SectionHead title="이런 코디는 어때요?" action="다른 코디" onAction={shuffle} />
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={s.cards}
            style={s.cardsWrap}
          >
            {candidates.map((o, i) => (
              <OutfitCard
                key={`${o.id}-${i}`}
                items={o.items}
                width={132}
                title={`코디 ${i + 1}`}
                sub={`${o.score}점`}
                selected={current.id === o.id}
                saved={!!saved && saved.outfit.id === o.id}
                onPress={() => setOverride(o)}
              />
            ))}
          </ScrollView>

          <SectionHead title={`${DAY_NAMES[offset]}의 추천 코디`} action="전체 바꾸기" onAction={shuffle} />
          {pinned.length > 0 && <Text style={s.pinNote}>고정한 아이템은 전체 바꾸기에서 유지돼요</Text>}
          <View style={s.board}>
            <OutfitBoard outfit={current} inset={12} />
          </View>
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
                <Icon name="check" size={18} color={c.primary} />
                <Text style={{ color: c.primary, fontWeight: '700', flex: 1 }}>
                  {offset === 0
                    ? '오늘 코디로 저장했어요'
                    : `${DAY_NAMES[offset]} 코디로 저장했어요 · 그날 아침 ‘오늘 코디’로 보여요`}
                </Text>
              </Row>
              {saved.feedback ? (
                <Text style={s.feedbackDone}>
                  피드백 고마워요 —{' '}
                  {saved.feedback === 'LIKE' ? '비슷한 조합을 더 추천할게요.' : '다음 추천에서 덜 보여드릴게요.'}
                </Text>
              ) : (
                <Row style={{ gap: 8 }}>
                  <Button
                    title="좋아요"
                    variant="outline"
                    small
                    onPress={() => setFeedback(date, 'LIKE')}
                    style={{ flex: 1 }}
                  />
                  <Button
                    title="별로예요"
                    variant="outline"
                    small
                    onPress={() => setDislikeOpen(true)}
                    style={{ flex: 1 }}
                  />
                </Row>
              )}
              {dislikeOpen && !saved.feedback && (
                <Card>
                  <Text style={s.dislikeHead}>어떤 점이 아쉬웠나요?</Text>
                  <ChipGroup
                    options={DISLIKE_REASONS}
                    labels={(r) => r}
                    value={dislikeReasons}
                    multi
                    small
                    onChange={setDislikeReasons}
                  />
                  <Button
                    title="보내기"
                    small
                    style={{ marginTop: 14 }}
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
                  variant="soft"
                  onPress={() => {
                    const look = looks.find((l) => l.date === date);
                    router.push(look ? `/look/${look.id}` : `/look/new?date=${date}`);
                  }}
                />
              )}
            </View>
          ) : (
            <Button
              title={saved ? '이 코디로 바꿔 저장' : `${DAY_NAMES[offset]} 코디로 저장 ✨`}
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

interface IStatCardProps {
  icon: IconName;
  label: string;
  value: string;
  unit: string;
  onPress?: () => void;
}

/** 보드의 "내 옷장" 통계 카드: 아이콘 · 이름 · 큰 숫자 + 단위 */
const StatCard = ({ icon, label, value, unit, onPress }: IStatCardProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <Pressable style={s.stat} onPress={onPress} disabled={!onPress} accessibilityRole={onPress ? 'button' : undefined}>
      <Icon name={icon} size={22} color={c.primary} />
      <Text style={s.statLabel} numberOfLines={1}>
        {label}
      </Text>
      <Text style={s.statValue}>
        {value}
        <Text style={s.statUnit}>{unit}</Text>
      </Text>
    </Pressable>
  );
};

const useStyles = makeStyles((t) => ({
  greet: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 4 },
  hello: { fontSize: 26, lineHeight: 34 },
  helloSub: { fontSize: 15, color: t.colors.textSub, marginTop: 8, lineHeight: 22 },
  bell: { position: 'absolute', top: 0, right: 0 },
  days: { flexDirection: 'row', gap: 8, marginTop: 10 },
  day: {
    flex: 1,
    paddingVertical: 9,
    alignItems: 'center',
    gap: 3,
    borderWidth: 1,
    borderRadius: t.radius.md,
  },
  dayOn: { backgroundColor: t.colors.primary, borderColor: t.colors.primary },
  dayOff: { backgroundColor: t.colors.surface, borderColor: t.colors.border },
  dayName: { fontSize: 13, fontWeight: '800' },
  dayTemp: { fontSize: 11, fontWeight: '600' },
  weather: { ...cardBase(t), flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16, marginTop: 12 },
  sun: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: t.colors.accentSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  weatherHead: { fontSize: 15, fontWeight: '800', color: t.colors.text },
  weatherSub: { fontSize: 13, color: t.colors.textSub, marginTop: 4, fontWeight: '600' },
  weatherMeta: { fontSize: 11, color: t.colors.textMuted, marginTop: 4 },
  notice: { backgroundColor: t.colors.primarySoft, padding: 12, marginTop: 8, borderRadius: t.radius.md },
  noticeText: { color: t.colors.primary, fontSize: 13, lineHeight: 19 },
  plan: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: t.colors.surface,
    borderWidth: 1,
    borderColor: t.colors.primary,
    borderRadius: t.radius.md,
    padding: 12,
    marginTop: 8,
  },
  planText: { color: t.colors.primary, fontSize: 13, flex: 1 },
  stats: { flexDirection: 'row', gap: 10 },
  stat: { ...cardBase(t), flex: 1, alignItems: 'center', paddingVertical: 16, paddingHorizontal: 6, gap: 6 },
  statLabel: { fontSize: 12, color: t.colors.textSub, fontWeight: '600' },
  statValue: { fontSize: 26, fontWeight: '800', color: t.colors.primary },
  statUnit: { fontSize: 13, fontWeight: '700', color: t.colors.primary },
  cardsWrap: { marginHorizontal: -20 },
  cards: { gap: 10, paddingHorizontal: 20, paddingBottom: 4 },
  board: { ...cardBase(t), padding: 12 },
  pinNote: { color: t.colors.textSub, fontSize: 12, marginTop: -6, marginBottom: 10 },
  empty: { marginTop: 18, alignItems: 'center', paddingVertical: 24 },
  emptySub: { color: t.colors.textSub, fontSize: 14, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  feedbackDone: { color: t.colors.textSub, fontSize: 13 },
  dislikeHead: { fontWeight: '700', marginBottom: 10, color: t.colors.text },
}));
