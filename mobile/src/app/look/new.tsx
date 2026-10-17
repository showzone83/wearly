import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { ChipGroup } from '@/components/Chip';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, Header, Kicker, Label, Screen, Serif, Stars } from '@/components/ui';
import { CATEGORY_LABEL, EMOTION_LABEL, OTHERS_LABEL, SLOTS } from '@/data/constants';
import type { DailyLook, Emotion, OthersReaction, OutfitItems, Slot } from '@/data/types';
import { pushWorn } from '@/lib/account';
import { dayContext, newId } from '@/lib/context';
import { longLabel, todayKey } from '@/lib/date';
import { diagnose } from '@/lib/diagnose';
import { slotOf } from '@/lib/recommend';
import { engineInput, useStore } from '@/store/useStore';
import { colors } from '@/theme';

// 목업 ⑤ 착장 기록 (p.17 · p.18): 입은 옷 + 나의 만족도 + 주변 반응 → AI 진단
export default function NewLook() {
  const params = useLocalSearchParams<{ date?: string }>();
  const date = params.date ?? todayKey();
  const allItems = useStore((s) => s.items);
  const profile = useStore((s) => s.profile);
  const saved = useStore((s) => s.dayOutfits[date]);
  const plan = useStore((s) => s.plans.find((p) => p.date === date));
  const addLook = useStore((s) => s.addLook);

  const initial = saved?.outfit.items ?? plan?.outfit?.items ?? {};
  const [items, setItems] = useState<OutfitItems>(initial);
  const [self, setSelf] = useState(4);
  const [emotions, setEmotions] = useState<Emotion[]>([]);
  const [memo, setMemo] = useState('');
  const [others, setOthers] = useState<OthersReaction>('NORMAL');
  const [praised, setPraised] = useState<string | undefined>();

  const chosen = Object.values(items).filter(Boolean) as string[];
  const canSave = !!items.TOP && !!items.BOTTOM;

  const submit = () => {
    const ctx = saved?.outfit.context ?? plan?.outfit?.context ?? dayContext(date, profile);
    const diagnosis = diagnose(items, engineInput({ ...ctx, date, excludeRecent: false }));
    const look: DailyLook = {
      id: newId('look'),
      date,
      items,
      occasion: ctx.occasion,
      selfScore: self,
      emotions,
      memo: memo.trim() || undefined,
      others,
      praisedItemId: others === 'PRAISE' ? praised : undefined,
      diagnosis,
      createdAt: new Date().toISOString(),
    };
    addLook(look);
    // 계정 모드: 착용 기록은 기기에만 두지만 착용 횟수·마지막 착용일은 서버 아이템에도 반영한다
    void pushWorn(Object.values(items).filter((id): id is string => !!id));
    router.replace(`/look/${look.id}`);
  };

  return (
    <Screen header={<Header title="착용 기록" />} footer={<Button title="기록하고 AI 진단 받기" variant="green" disabled={!canSave} onPress={submit} />}>
      <Kicker>{longLabel(date)}</Kicker>
      <Serif style={s.title}>오늘 입은 옷을 남겨 주세요</Serif>
      <Text style={s.desc}>
        {saved ? '저장한 코디를 불러왔어요. 실제로 바꿔 입은 옷이 있다면 고쳐 주세요.' : '입은 옷을 칸마다 골라 주세요. 착장 사진 자동 인식은 출시 버전에서 지원해요.'}
      </Text>

      <View style={{ gap: 16, marginTop: 20 }}>
        {SLOTS.map((slot) => (
          <SlotPicker key={slot} slot={slot} value={items[slot]} items={allItems.filter((i) => slotOf(i.category) === slot)} onChange={(id) => setItems({ ...items, [slot]: id })} />
        ))}
      </View>

      <Text style={s.section}>Self · 나의 만족도</Text>
      <Stars value={self} size={30} onChange={setSelf} />
      <View style={{ marginTop: 14 }}>
        <ChipGroup options={Object.keys(EMOTION_LABEL) as Emotion[]} labels={EMOTION_LABEL} value={emotions} multi small onChange={setEmotions} />
      </View>
      <TextInput value={memo} onChangeText={setMemo} placeholder="한 줄 메모 · 예: 하루 종일 편했다" placeholderTextColor={colors.faint} style={s.input} />

      <Text style={s.section}>Others · 주변 만족도</Text>
      <ChipGroup options={Object.keys(OTHERS_LABEL) as OthersReaction[]} labels={OTHERS_LABEL} value={others} onChange={([o]) => setOthers(o)} />
      {others === 'PRAISE' && chosen.length > 0 && (
        <View style={{ marginTop: 14 }}>
          <Label>어떤 아이템을 칭찬받았나요?</Label>
          <ChipGroup
            options={chosen}
            labels={(id) => allItems.find((i) => i.id === id)?.name ?? ''}
            value={praised}
            small
            allowEmpty
            onChange={([id]) => setPraised(id)}
          />
        </View>
      )}
      <Text style={s.note}>지인 익명 투표(링크 공유)는 출시 버전에서 지원해요.</Text>
    </Screen>
  );
}

function SlotPicker({ slot, value, items, onChange }: { slot: Slot; value?: string; items: ReturnType<typeof useStore.getState>['items']; onChange: (id?: string) => void }) {
  if (items.length === 0) return null;
  return (
    <View>
      <Label>
        {CATEGORY_LABEL[slot]}
        {(slot === 'TOP' || slot === 'BOTTOM') && <Text style={{ color: colors.danger }}> *</Text>}
      </Label>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 20 }}>
        {items.map((it) => {
          const on = value === it.id;
          return (
            <Pressable key={it.id} onPress={() => onChange(on ? undefined : it.id)} style={[s.pick, on && s.pickOn]}>
              <ItemThumb item={it} size={64} />
              <Text style={s.pickName} numberOfLines={1}>
                {it.name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  title: { fontSize: 23, marginTop: 8 },
  desc: { color: colors.sub, fontSize: 13, lineHeight: 19, marginTop: 8 },
  section: { fontSize: 16, fontWeight: '800', color: colors.ink, marginTop: 30, marginBottom: 12 },
  input: { height: 48, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14, fontSize: 15, color: colors.ink, marginTop: 14 },
  pick: { width: 72, padding: 2, borderWidth: 2, borderColor: 'transparent' },
  pickOn: { borderColor: colors.green },
  pickName: { fontSize: 11, marginTop: 4, color: colors.ink },
  note: { color: colors.faint, fontSize: 12, marginTop: 14 },
});
