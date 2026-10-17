import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';

import { Chip, ChipGroup } from '@/components/Chip';
import { condIcon, Icon } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { Mascot } from '@/components/Mascot';
import { Button, Label, notify, PageTitle, Row, Screen, Title } from '@/components/ui';
import { FORMALITY_LABEL, MOOD_LABEL, OCCASION_LABEL, PLAN_OCCASIONS, TIME_LABEL, WEEKDAY_SHORT, WORK_OCCASIONS } from '@/data/constants';
import type { ColorKey, Formality, Mood, Occasion, Outfit, RecommendContext, StyleTag, TimeOfDay } from '@/data/types';
import type { RecommendSource } from '@/lib/aiOutfits';
import { requestAiOutfits, ruleOutfits, type AiPhase } from '@/lib/aiRecommend';
import { activeCodes, colorLabel, isActiveCode, styleLabel, withCurrent } from '@/lib/codes';
import { dayContext } from '@/lib/context';
import { addDays, diffDays, fromKey, longLabel, todayKey } from '@/lib/date';
import { canRecommend, isCandidate, slotOf } from '@/lib/recommend';
import { COND_LABEL, FEEL_PRESETS, forecast, hasForecast, manualWeather } from '@/lib/weather';
import { useCodesVersion } from '@/store/useCodes';
import { useStore } from '@/store/useStore';
import { inputBase, makeStyles, useColors } from '@/theme';

type Mode = 'TODAY' | 'PLAN';

/** AI 추천 진행 단계 (doc/09 5장) */
const PHASES: { key: AiPhase; label: string }[] = [
  { key: 'look', label: '옷장을 살펴보는 중' },
  { key: 'pick', label: '조합을 고르는 중' },
  { key: 'score', label: '점수를 매기는 중' },
];
/** '옷장을 살펴보는 중'을 보여 주는 최소 시간 */
const LOOK_MS = 700;
/** 결과가 온 뒤 '점수를 매기는 중'을 보여 주는 시간 */
const SCORE_MS = 450;

type Failure =
  | { kind: 'limit'; message: string }
  | { kind: 'insufficient'; missing: string }
  | { kind: 'error'; message: string };

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

function planFormality(o: Occasion): Formality {
  if (o === 'INTERVIEW' || o === 'WEDDING') return 'FORMAL';
  if (o === 'DATE' || o === 'TRAVEL' || o === 'GATHERING') return 'CASUAL';
  return 'BIZ_CASUAL';
}

/** 예정일 컨셉(STYLE 코드) → 분위기. 모르는 스타일은 단정으로 본다 */
function planMood(concepts: StyleTag[]): Mood {
  if (concepts.some((c) => c === 'CASUAL' || c === 'STREET' || c === 'SPORTY')) return 'COMFY';
  if (concepts.some((c) => c === 'MINIMAL' || c === 'CLASSIC' || c === 'FEMININE')) return 'CHIC';
  return 'NEAT';
}

// 목업 ④ AI 코디 (p.15 조건 선택 · p.16 예정일 코디)
export default function Coordi() {
  const params = useLocalSearchParams<{ mode?: string }>();
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const setDraft = useStore((s) => s.setDraft);
  useCodesVersion(); // 코드가 갱신되면 다시 그린다
  const s = useStyles();
  const colors = useColors();
  const today = todayKey();
  const base = dayContext(today, profile);

  const [mode, setMode] = useState<Mode>('TODAY');
  useEffect(() => {
    if (params.mode === 'plan') setMode('PLAN');
  }, [params.mode]);

  // 오늘 코디 조건
  const [occasion, setOccasion] = useState<Occasion>(base.occasion);
  const [mood, setMood] = useState<Mood>(base.mood);
  const [formality, setFormality] = useState<Formality>(base.formality);
  const [anchor, setAnchor] = useState<string | undefined>();
  const [excludeRecent, setExcludeRecent] = useState(true);
  const [avoid, setAvoid] = useState<ColorKey[]>([]);

  // 예정일 조건
  const [planDate, setPlanDate] = useState(addDays(today, 7));
  const [time, setTime] = useState<TimeOfDay>('PM');
  const [concepts, setConcepts] = useState<StyleTag[]>(() => (isActiveCode('STYLE', 'MINIMAL') ? ['MINIMAL'] : []));
  const [planOcc, setPlanOcc] = useState<Occasion>('WEDDING');
  const [feel, setFeel] = useState(FEEL_PRESETS[2].temp);
  const [rain, setRain] = useState(false);
  const [memo, setMemo] = useState('');

  const ready = canRecommend(items);
  const auto = hasForecast(planDate);
  const planWeather = auto ? forecast(planDate) : manualWeather(feel, rain);
  const todayWeather = forecast(today);
  const anchorable = items.filter((i) => isCandidate(i) && slotOf(i.category) !== null);

  /** 지금 화면 조건 (오늘 코디: 오늘 예보 · 예정일 코디: 그날 예보 또는 직접 고른 기온) */
  const buildCtx = (): RecommendContext =>
    mode === 'TODAY'
      ? { occasion, mood, formality, weather: todayWeather, anchorItemId: anchor, excludeRecent, avoidColors: avoid, date: today }
      : {
          occasion: planOcc,
          mood: planMood(concepts),
          formality: planFormality(planOcc),
          concepts,
          weather: planWeather,
          anchorItemId: anchor,
          avoidColors: avoid,
          date: planDate,
        };

  const openResult = (outfits: Outfit[], source: RecommendSource) => {
    setDraft({
      mode,
      outfits,
      source,
      plan:
        mode === 'PLAN'
          ? { date: planDate, timeOfDay: time, title: OCCASION_LABEL[planOcc], occasion: planOcc, concepts, weather: planWeather, memo: memo.trim() || undefined }
          : undefined,
    });
    router.push('/result');
  };

  // AI 추천 진행·오류 (null이면 조건 입력 화면)
  const [phase, setPhase] = useState<number | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  /** 취소하거나 다시 누르면 늦게 온 결과를 버린다 */
  const runRef = useRef(0);

  const reset = () => {
    runRef.current++;
    setPhase(null);
    setFailure(null);
  };

  /** "코디 추천 받기": 서버 AI 후보 → 엔진 점수 → 상위 3개 */
  const submit = async () => {
    const run = ++runRef.current;
    const live = () => runRef.current === run;
    const ctx = buildCtx();
    setFailure(null);
    setPhase(0);
    const started = Date.now();
    const toPick = setTimeout(() => live() && setPhase((p) => (p === null ? p : Math.max(p, 1))), LOOK_MS);
    const outcome = await requestAiOutfits(ctx, {
      plan: mode === 'PLAN' ? { timeOfDay: time, memo } : undefined,
      onPhase: (p) => {
        if (p === 'score' && live()) setPhase(2);
      },
    });
    clearTimeout(toPick);
    if (!live()) return;

    if (outcome.kind === 'ok') {
      if (outcome.source.method === 'AI') await wait(Math.max(SCORE_MS, LOOK_MS - (Date.now() - started)));
      if (!live()) return;
      openResult(outcome.outfits, outcome.source);
      reset();
      return;
    }
    setPhase(null);
    setFailure(outcome);
  };

  /** 규칙 엔진 추천 (AI 실패·한도 초과 때 사용자가 고른다) */
  const submitRule = () => {
    const outfits = ruleOutfits(buildCtx());
    if (outfits.length === 0) {
      notify('추천할 수 없어요', '조건에 맞는 옷이 부족해요. 피하고 싶은 색이나 고정 아이템을 줄여 보세요.');
      return;
    }
    openResult(outfits, { method: 'RULE' });
    reset();
  };

  const planDates = Array.from({ length: 60 }, (_, i) => addDays(today, i + 1));

  return (
    <Screen
      header={<PageTitle title="코디 추천" sub="내 옷장 속 아이템으로 AI가 코디를 완성해요" />}
      footer={
        ready.ok && phase === null && !failure ? (
          <Button
            title="코디 추천 받기"
            icon={<Icon name="sparkle" color={colors.onPrimary} size={18} filled />}
            onPress={() => void submit()}
          />
        ) : undefined
      }
    >
      <View style={s.segment}>
        {(['TODAY', 'PLAN'] as Mode[]).map((m) => (
          <Pressable key={m} onPress={() => setMode(m)} disabled={phase !== null} style={[s.segItem, mode === m && s.segOn]}>
            <Text style={[s.segText, mode === m && { color: colors.onPrimary, fontWeight: '800' }]}>{m === 'TODAY' ? '오늘 코디' : '예정일 코디'}</Text>
          </Pressable>
        ))}
      </View>

      {phase !== null ? (
        <AiProgress phase={phase} onCancel={reset} />
      ) : failure ? (
        <FailurePanel failure={failure} onRule={submitRule} onRetry={() => void submit()} onBack={reset} />
      ) : !ready.ok ? (
        <View style={{ paddingTop: 20, alignItems: 'center', gap: 14 }}>
          <Mascot pose="closet" size={160} sceneWidth={290} />
          <Text style={{ color: colors.textSub, textAlign: 'center', lineHeight: 21 }}>
            코디를 만들려면 {ready.missing.join(', ')}이(가) 필요해요.{'\n'}(원피스 한 벌로도 시작할 수 있어요) 옷장에 먼저 등록해 주세요.
          </Text>
          <Button title="옷 등록하기" onPress={() => router.push('/item/new')} style={{ alignSelf: 'stretch' }} />
        </View>
      ) : mode === 'TODAY' ? (
        <View style={{ gap: 24 }}>
          <View>
            <Title style={s.headline}>오늘 어떤 자리에 가세요?</Title>
            <Row style={{ gap: 6, marginTop: 8 }}>
              <Icon name={condIcon(todayWeather.cond)} size={16} color={colors.textSub} />
              <Text style={s.weatherLine}>
                {longLabel(today)} · {todayWeather.temp}° {COND_LABEL[todayWeather.cond]} (예보 자동 반영)
              </Text>
            </Row>
          </View>
          <View>
            <Label>상황</Label>
            <ChipGroup options={[...WORK_OCCASIONS, 'DAILY'] as Occasion[]} labels={OCCASION_LABEL} value={occasion} onChange={([o]) => setOccasion(o)} />
          </View>
          <View>
            <Label>분위기</Label>
            <ChipGroup options={['NEAT', 'COMFY', 'CHIC'] as Mood[]} labels={MOOD_LABEL} value={mood} onChange={([m]) => setMood(m)} />
          </View>
          <View>
            <Label>격식</Label>
            <ChipGroup options={['BIZ_CASUAL', 'FORMAL', 'CASUAL'] as Formality[]} labels={FORMALITY_LABEL} value={formality} onChange={([f]) => setFormality(f)} />
          </View>
          <AnchorPicker items={anchorable} value={anchor} onChange={setAnchor} />
          <Row style={s.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={s.switchTitle}>최근 착용 아이템 제외</Text>
              <Text style={s.switchSub}>최근 3일 안에 입은 옷은 빼고 추천해요</Text>
            </View>
            <Switch value={excludeRecent} onValueChange={setExcludeRecent} trackColor={{ true: colors.primary }} />
          </Row>
          <AvoidColors value={avoid} onChange={setAvoid} />
        </View>
      ) : (
        <View style={{ gap: 24 }}>
          <Title style={s.headline}>언제 입을 코디인가요?</Title>
          <View>
            <Label>날짜 · 시간대</Label>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6, paddingRight: 20 }}>
              {planDates.map((d) => {
                const on = d === planDate;
                const dt = fromKey(d);
                return (
                  <Pressable key={d} onPress={() => setPlanDate(d)} style={[s.dateChip, on ? s.dateOn : s.dateOff]}>
                    <Text style={[s.dateW, { color: on ? colors.onPrimary : colors.textSub }]}>
                      {dt.getDate() === 1 || d === planDates[0] ? `${dt.getMonth() + 1}월` : WEEKDAY_SHORT[dt.getDay()]}
                    </Text>
                    <Text style={[s.dateN, { color: on ? colors.onPrimary : colors.text }]}>{dt.getDate()}</Text>
                  </Pressable>
                );
              })}
            </ScrollView>
            <Text style={s.weatherLine}>
              {longLabel(planDate)} · D-{diffDays(planDate, today)}
            </Text>
            <View style={{ marginTop: 10 }}>
              <ChipGroup options={['AM', 'PM', 'EVENING'] as TimeOfDay[]} labels={TIME_LABEL} value={time} small onChange={([t]) => setTime(t)} />
            </View>
          </View>
          <View>
            <Label>컨셉</Label>
            <ChipGroup options={withCurrent(activeCodes('STYLE'), concepts)} labels={styleLabel} value={concepts} multi onChange={setConcepts} />
          </View>
          <View>
            <Label>상황 (TPO)</Label>
            <ChipGroup options={PLAN_OCCASIONS} labels={OCCASION_LABEL} value={planOcc} onChange={([o]) => setPlanOcc(o)} />
          </View>
          <View>
            <Label>날씨</Label>
            {auto ? (
              <View style={s.weatherBox}>
                <Icon name={condIcon(planWeather.cond)} size={22} color={colors.primary} />
                <Text style={s.weatherBoxText}>
                  예보 자동 · {planWeather.temp}°C {COND_LABEL[planWeather.cond]} · 강수 {planWeather.rainProb}%
                </Text>
              </View>
            ) : (
              <View style={{ gap: 10 }}>
                <Text style={s.switchSub}>예보 기간(10일) 이후라 기온을 직접 골라 주세요. D-1에 최신 예보로 다시 확인해요.</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {FEEL_PRESETS.map((p) => (
                    <Chip key={p.label} label={`${p.label} ${p.temp}°`} selected={feel === p.temp} onPress={() => setFeel(p.temp)} small />
                  ))}
                  <Chip label="비 예상" selected={rain} onPress={() => setRain(!rain)} small />
                </View>
              </View>
            )}
          </View>
          <AnchorPicker items={anchorable} value={anchor} onChange={setAnchor} title="꼭 입을 아이템 고정 (선택)" />
          <AvoidColors value={avoid} onChange={setAvoid} />
          <View>
            <Label>동행 · 장소 메모</Label>
            <TextInput value={memo} onChangeText={setMemo} placeholder="예: 야외 정원 예식, 오래 서 있어야 함" placeholderTextColor={colors.textMuted} style={s.input} />
          </View>
        </View>
      )}
    </Screen>
  );
}

/** AI 추천 진행 표시: 옷장을 살펴보는 중 → 조합을 고르는 중 → 점수를 매기는 중 */
function AiProgress({ phase, onCancel }: { phase: number; onCancel: () => void }) {
  const s = useStyles();
  const colors = useColors();
  return (
    <View style={s.panel}>
      <Mascot pose="style" size={140} />
      <ActivityIndicator color={colors.primary} style={{ marginTop: 8 }} />
      <Title style={s.panelTitle}>AI가 내 옷장에서{'\n'}코디를 고르고 있어요</Title>
      <Text style={s.panelSub}>보통 몇 초 걸려요</Text>
      <View style={{ gap: 12, marginTop: 22, alignSelf: 'center' }}>
        {PHASES.map((p, i) => {
          const done = i < phase;
          const active = i === phase;
          return (
            <Row key={p.key} style={{ gap: 10 }}>
              {active ? (
                <ActivityIndicator size="small" color={colors.primary} style={{ width: 18 }} />
              ) : (
                <Icon name="check" size={18} color={done ? colors.primary : colors.border} />
              )}
              <Text style={{ color: done || active ? colors.text : colors.textMuted, fontWeight: active ? '800' : '600', fontSize: 15 }}>{p.label}</Text>
            </Row>
          );
        })}
      </View>
      <Pressable onPress={onCancel} hitSlop={10} style={{ marginTop: 30 }} accessibilityRole="button">
        <Text style={s.link}>취소</Text>
      </Pressable>
    </View>
  );
}

/** AI 추천 오류: 한도 · 옷 모자람 · 그 밖의 실패 (doc/09 5장) */
function FailurePanel({
  failure,
  onRule,
  onRetry,
  onBack,
}: {
  failure: Failure;
  onRule: () => void;
  onRetry: () => void;
  onBack: () => void;
}) {
  const s = useStyles();
  const colors = useColors();
  const title =
    failure.kind === 'limit' ? '오늘 AI 코디 추천을 모두 썼어요' : failure.kind === 'insufficient' ? '코디를 만들 옷이 모자라요' : 'AI 추천을 받지 못했어요';
  const body =
    failure.kind === 'limit'
      ? `${failure.message}\n규칙 엔진 추천은 횟수 제한 없이 쓸 수 있어요.`
      : failure.kind === 'insufficient'
        ? `${failure.missing}\n추천에 쓸 수 있는 옷(세탁·수선 중 제외)으로 상의와 하의(또는 원피스), 신발이 있어야 해요.`
        : failure.message;
  return (
    <View style={s.panel}>
      <Title style={s.panelTitle}>{title}</Title>
      <Text style={[s.panelSub, { lineHeight: 21 }]}>{body}</Text>
      <View style={{ gap: 10, marginTop: 26, alignSelf: 'stretch' }}>
        {failure.kind === 'insufficient' ? (
          <Button title="옷 등록하기" icon={<Icon name="plus" color={colors.onPrimary} size={18} />} onPress={() => router.push('/item/new')} />
        ) : (
          <Button title="규칙 엔진으로 추천 받기" variant="green" onPress={onRule} />
        )}
        {failure.kind === 'error' && <Button title="AI 추천 다시 시도" variant="outline" onPress={onRetry} />}
        <Button title="조건 다시 고르기" variant="ghost" onPress={onBack} />
      </View>
    </View>
  );
}

function AnchorPicker({
  items,
  value,
  onChange,
  title = '중심 아이템 (선택)',
}: {
  items: ReturnType<typeof useStore.getState>['items'];
  value?: string;
  onChange: (id?: string) => void;
  title?: string;
}) {
  const s = useStyles();
  const colors = useColors();
  return (
    <View>
      <Label>{title}</Label>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 20 }}>
        {items.map((it) => {
          const on = value === it.id;
          return (
            <Pressable key={it.id} onPress={() => onChange(on ? undefined : it.id)} style={[s.anchor, on && { borderColor: colors.primary, borderWidth: 2 }]}>
              <ItemThumb item={it} size={68} />
              <Text style={s.anchorName} numberOfLines={1}>
                {it.name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

function AvoidColors({ value, onChange }: { value: ColorKey[]; onChange: (v: ColorKey[]) => void }) {
  return (
    <View>
      <Label>피하고 싶은 색</Label>
      <ChipGroup options={withCurrent(activeCodes('COLOR'), value)} labels={colorLabel} value={value} multi dots small onChange={onChange} />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  segment: {
    flexDirection: 'row',
    backgroundColor: t.colors.surfaceAlt,
    borderRadius: t.radius.pill,
    padding: 4,
    marginBottom: 24,
  },
  segItem: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: t.radius.pill },
  segOn: { backgroundColor: t.colors.primary },
  segText: { fontSize: 15, color: t.colors.textSub, fontWeight: '600' },
  headline: { fontSize: 23, lineHeight: 32 },
  weatherLine: { color: t.colors.textSub, fontSize: 13, marginTop: 8 },
  switchRow: { gap: 12 },
  switchTitle: { fontSize: 14, fontWeight: '700', color: t.colors.text },
  switchSub: { fontSize: 12, color: t.colors.textSub, marginTop: 2, lineHeight: 18 },
  dateChip: {
    width: 50,
    height: 62,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderRadius: t.radius.md,
  },
  dateOn: { backgroundColor: t.colors.primary, borderColor: t.colors.primary },
  dateOff: { backgroundColor: t.colors.surface, borderColor: t.colors.border },
  dateW: { fontSize: 11, fontWeight: '700' },
  dateN: { fontSize: 18, fontWeight: '800', marginTop: 2 },
  weatherBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: t.colors.primarySoft,
    borderRadius: t.radius.md,
    padding: 14,
  },
  weatherBoxText: { color: t.colors.primary, fontWeight: '700', fontSize: 14 },
  anchor: { width: 78, borderWidth: 2, borderColor: 'transparent', borderRadius: 16, padding: 3 },
  anchorName: { fontSize: 11, color: t.colors.text, marginTop: 4, textAlign: 'center' },
  panel: { alignItems: 'center', paddingTop: 16, paddingHorizontal: 4 },
  panelTitle: { fontSize: 22, lineHeight: 32, marginTop: 12, textAlign: 'center' },
  panelSub: { color: t.colors.textSub, fontSize: 14, marginTop: 10, textAlign: 'center' },
  link: { color: t.colors.primary, fontWeight: '700', textDecorationLine: 'underline' },
  input: inputBase(t),
}));
