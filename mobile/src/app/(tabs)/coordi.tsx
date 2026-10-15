import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';

import { Chip, ChipGroup } from '@/components/Chip';
import { condIcon, Icon } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, Label, notify, Row, Screen, Serif } from '@/components/ui';
import {
  COLOR,
  COLOR_KEYS,
  FORMALITY_LABEL,
  MOOD_LABEL,
  OCCASION_LABEL,
  PLAN_OCCASIONS,
  STYLE_LABEL,
  STYLE_TAGS,
  TIME_LABEL,
  WEEKDAY_SHORT,
  WORK_OCCASIONS,
} from '@/data/constants';
import type { ColorKey, Formality, Mood, Occasion, RecommendContext, StyleTag, TimeOfDay } from '@/data/types';
import { dayContext } from '@/lib/context';
import { addDays, diffDays, fromKey, longLabel, todayKey } from '@/lib/date';
import { canRecommend, recommend } from '@/lib/recommend';
import { COND_LABEL, FEEL_PRESETS, forecast, hasForecast, manualWeather } from '@/lib/weather';
import { engineInput, useStore } from '@/store/useStore';
import { colors } from '@/theme';

type Mode = 'TODAY' | 'PLAN';

function planFormality(o: Occasion): Formality {
  if (o === 'INTERVIEW' || o === 'WEDDING') return 'FORMAL';
  if (o === 'DATE' || o === 'TRAVEL' || o === 'GATHERING') return 'CASUAL';
  return 'BIZ_CASUAL';
}

function planMood(concepts: StyleTag[]): Mood {
  if (concepts.some((c) => c === 'CASUAL' || c === 'STREET')) return 'COMFY';
  if (concepts.some((c) => c === 'MINIMAL' || c === 'CLASSIC')) return 'CHIC';
  return 'NEAT';
}

// 목업 ④ AI 코디 (p.15 조건 선택 · p.16 예정일 코디)
export default function Coordi() {
  const params = useLocalSearchParams<{ mode?: string }>();
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const setDraft = useStore((s) => s.setDraft);
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
  const [concepts, setConcepts] = useState<StyleTag[]>(['MINIMAL']);
  const [planOcc, setPlanOcc] = useState<Occasion>('WEDDING');
  const [feel, setFeel] = useState(FEEL_PRESETS[2].temp);
  const [rain, setRain] = useState(false);
  const [memo, setMemo] = useState('');

  const ready = canRecommend(items);
  const auto = hasForecast(planDate);
  const planWeather = auto ? forecast(planDate) : manualWeather(feel, rain);
  const todayWeather = forecast(today);
  const anchorable = items.filter((i) => i.status === 'OWNED' && i.category !== 'ACC');

  const submit = () => {
    const ctx: RecommendContext =
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
    const outfits = recommend(engineInput(ctx), 3);
    if (outfits.length === 0) {
      notify('추천할 수 없어요', '조건에 맞는 옷이 부족해요. 피하고 싶은 색이나 고정 아이템을 줄여 보세요.');
      return;
    }
    setDraft({
      mode,
      outfits,
      plan:
        mode === 'PLAN'
          ? { date: planDate, timeOfDay: time, title: OCCASION_LABEL[planOcc], occasion: planOcc, concepts, weather: planWeather, memo: memo.trim() || undefined }
          : undefined,
    });
    router.push('/result');
  };

  const planDates = Array.from({ length: 60 }, (_, i) => addDays(today, i + 1));

  return (
    <Screen
      header={
        <View style={s.top}>
          <Text style={s.title}>AI 코디</Text>
        </View>
      }
      footer={ready.ok ? <Button title="코디 추천 받기" variant="green" icon={<Icon name="sparkle" color="#fff" size={18} />} onPress={submit} /> : undefined}
    >
      <View style={s.segment}>
        {(['TODAY', 'PLAN'] as Mode[]).map((m) => (
          <Pressable key={m} onPress={() => setMode(m)} style={[s.segItem, mode === m && s.segOn]}>
            <Text style={[s.segText, mode === m && { color: colors.ink, fontWeight: '800' }]}>{m === 'TODAY' ? '오늘 코디' : '예정일 코디'}</Text>
          </Pressable>
        ))}
      </View>

      {!ready.ok ? (
        <View style={{ paddingTop: 40, alignItems: 'center', gap: 14 }}>
          <Text style={{ color: colors.sub, textAlign: 'center', lineHeight: 21 }}>
            코디를 만들려면 {ready.missing.join(', ')}이(가) 필요해요.{'\n'}옷장에 먼저 등록해 주세요.
          </Text>
          <Button title="옷 등록하기" onPress={() => router.push('/item/new')} style={{ alignSelf: 'stretch' }} />
        </View>
      ) : mode === 'TODAY' ? (
        <View style={{ gap: 24 }}>
          <View>
            <Serif style={s.headline}>오늘 어떤 자리에 가세요?</Serif>
            <Row style={{ gap: 6, marginTop: 8 }}>
              <Icon name={condIcon(todayWeather.cond)} size={16} color={colors.sub} />
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
            <Switch value={excludeRecent} onValueChange={setExcludeRecent} trackColor={{ true: colors.green }} />
          </Row>
          <AvoidColors value={avoid} onChange={setAvoid} />
        </View>
      ) : (
        <View style={{ gap: 24 }}>
          <Serif style={s.headline}>언제 입을 코디인가요?</Serif>
          <View>
            <Label>날짜 · 시간대</Label>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6, paddingRight: 20 }}>
              {planDates.map((d) => {
                const on = d === planDate;
                const dt = fromKey(d);
                return (
                  <Pressable key={d} onPress={() => setPlanDate(d)} style={[s.dateChip, on ? s.dateOn : s.dateOff]}>
                    <Text style={[s.dateW, { color: on ? colors.beige : colors.sub }]}>
                      {dt.getDate() === 1 || d === planDates[0] ? `${dt.getMonth() + 1}월` : WEEKDAY_SHORT[dt.getDay()]}
                    </Text>
                    <Text style={[s.dateN, { color: on ? '#fff' : colors.ink }]}>{dt.getDate()}</Text>
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
            <ChipGroup options={STYLE_TAGS} labels={STYLE_LABEL} value={concepts} multi onChange={setConcepts} />
          </View>
          <View>
            <Label>상황 (TPO)</Label>
            <ChipGroup options={PLAN_OCCASIONS} labels={OCCASION_LABEL} value={planOcc} onChange={([o]) => setPlanOcc(o)} />
          </View>
          <View>
            <Label>날씨</Label>
            {auto ? (
              <View style={s.weatherBox}>
                <Icon name={condIcon(planWeather.cond)} size={22} color={colors.green} />
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
            <TextInput value={memo} onChangeText={setMemo} placeholder="예: 야외 정원 예식, 오래 서 있어야 함" placeholderTextColor={colors.faint} style={s.input} />
          </View>
        </View>
      )}
    </Screen>
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
  return (
    <View>
      <Label>{title}</Label>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 20 }}>
        {items.map((it) => {
          const on = value === it.id;
          return (
            <Pressable key={it.id} onPress={() => onChange(on ? undefined : it.id)} style={[s.anchor, on && { borderColor: colors.green, borderWidth: 2 }]}>
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
      <ChipGroup options={COLOR_KEYS.slice(0, 10)} labels={(c) => COLOR[c].label} value={value} multi dots small onChange={onChange} />
    </View>
  );
}

const s = StyleSheet.create({
  top: { paddingHorizontal: 20, height: 52, justifyContent: 'center' },
  title: { fontSize: 18, fontWeight: '800', color: colors.ink, letterSpacing: 2 },
  segment: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.line, marginBottom: 22 },
  segItem: { flex: 1, alignItems: 'center', paddingVertical: 12 },
  segOn: { borderBottomWidth: 2, borderBottomColor: colors.green, marginBottom: -1 },
  segText: { fontSize: 15, color: colors.faint, fontWeight: '600' },
  headline: { fontSize: 23, lineHeight: 33 },
  weatherLine: { color: colors.sub, fontSize: 13, marginTop: 8 },
  switchRow: { gap: 12 },
  switchTitle: { fontSize: 14, fontWeight: '700', color: colors.ink },
  switchSub: { fontSize: 12, color: colors.sub, marginTop: 2, lineHeight: 18 },
  dateChip: { width: 48, height: 60, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  dateOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  dateOff: { backgroundColor: colors.surface, borderColor: colors.line },
  dateW: { fontSize: 11, fontWeight: '700' },
  dateN: { fontSize: 18, fontWeight: '800', marginTop: 2 },
  weatherBox: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: colors.greenSoft, padding: 14 },
  weatherBoxText: { color: colors.green, fontWeight: '700', fontSize: 14 },
  anchor: { width: 76, borderWidth: 1, borderColor: 'transparent', padding: 3 },
  anchorName: { fontSize: 11, color: colors.ink, marginTop: 4 },
  input: { height: 48, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14, fontSize: 15, color: colors.ink },
});
