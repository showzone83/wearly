import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';

import { ChipGroup } from '@/components/Chip';
import { ItemThumb } from '@/components/ItemThumb';
import { Button, Header, Kicker, Label, Screen, Stars, Title } from '@/components/ui';
import { EMOTION_LABEL, OTHERS_LABEL, SLOTS } from '@/data/constants';
import type { DailyLook, Emotion, OthersReaction, OutfitItems, Slot } from '@/data/types';
import { pushWorn } from '@/lib/account';
import { slotLabel } from '@/lib/codes';
import { dayContext, newId } from '@/lib/context';
import { longLabel, todayKey } from '@/lib/date';
import { diagnose } from '@/lib/diagnose';
import { slotOf } from '@/lib/recommend';
import { engineInput, useStore } from '@/store/useStore';
import { inputBase, makeStyles, useColors } from '@/theme';

// 목업 ⑤ 착장 기록 (p.17 · p.18): 입은 옷 + 나의 만족도 + 주변 반응 → AI 진단
export default function NewLook() {
  const params = useLocalSearchParams<{ date?: string }>();
  const date = params.date ?? todayKey();
  const allItems = useStore((s) => s.items);
  const profile = useStore((s) => s.profile);
  const saved = useStore((s) => s.dayOutfits[date]);
  const plan = useStore((s) => s.plans.find((p) => p.date === date));
  const addLook = useStore((s) => s.addLook);
  const s = useStyles();
  const colors = useColors();

  const initial = saved?.outfit.items ?? plan?.outfit?.items ?? {};
  const [items, setItems] = useState<OutfitItems>(initial);
  const [self, setSelf] = useState(4);
  const [emotions, setEmotions] = useState<Emotion[]>([]);
  const [memo, setMemo] = useState('');
  const [others, setOthers] = useState<OthersReaction>('NORMAL');
  const [praised, setPraised] = useState<string | undefined>();

  const chosen = Object.values(items).filter(Boolean) as string[];
  // 상의+하의 또는 원피스 한 벌
  const canSave = (!!items.TOP && !!items.BOTTOM) || !!items.DRESS;

  /** 원피스를 고르면 상의·하의를, 상의·하의를 고르면 원피스를 비운다 */
  const pickSlot = (slot: Slot, id?: string) => {
    const next: OutfitItems = { ...items, [slot]: id };
    if (id && slot === 'DRESS') {
      delete next.TOP;
      delete next.BOTTOM;
    }
    if (id && (slot === 'TOP' || slot === 'BOTTOM')) delete next.DRESS;
    setItems(next);
  };

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
      <Kicker color={colors.primary}>{longLabel(date)}</Kicker>
      <Title style={s.title}>오늘 입은 옷을 남겨 주세요</Title>
      <Text style={s.desc}>
        {saved
          ? '저장한 코디를 불러왔어요. 실제로 바꿔 입은 옷이 있다면 고쳐 주세요.'
          : '입은 옷을 칸마다 골라 주세요. 상의·하의 대신 원피스 한 벌도 돼요. 착장 사진 자동 인식은 출시 버전에서 지원해요.'}
      </Text>

      <View style={{ gap: 16, marginTop: 20 }}>
        {SLOTS.map((slot) => (
          <SlotPicker
            key={slot}
            slot={slot}
            value={items[slot]}
            required={(slot === 'TOP' || slot === 'BOTTOM') && !items.DRESS}
            items={allItems.filter((i) => slotOf(i.category) === slot)}
            onChange={(id) => pickSlot(slot, id)}
          />
        ))}
      </View>

      <Text style={s.section}>Self · 나의 만족도</Text>
      <Stars value={self} size={30} onChange={setSelf} />
      <View style={{ marginTop: 14 }}>
        <ChipGroup options={Object.keys(EMOTION_LABEL) as Emotion[]} labels={EMOTION_LABEL} value={emotions} multi small onChange={setEmotions} />
      </View>
      <TextInput value={memo} onChangeText={setMemo} placeholder="한 줄 메모 · 예: 하루 종일 편했다" placeholderTextColor={colors.textMuted} style={s.input} />

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

function SlotPicker({
  slot,
  value,
  required,
  items,
  onChange,
}: {
  slot: Slot;
  value?: string;
  required: boolean;
  items: ReturnType<typeof useStore.getState>['items'];
  onChange: (id?: string) => void;
}) {
  const s = useStyles();
  const colors = useColors();
  if (items.length === 0) return null;
  return (
    <View>
      <Label>
        {slotLabel(slot)}
        {required && <Text style={{ color: colors.danger }}> *</Text>}
        {slot === 'DRESS' && <Text style={{ color: colors.textMuted, fontWeight: '400' }}> (고르면 상의·하의 대신)</Text>}
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

const useStyles = makeStyles((t) => ({
  title: { fontSize: 24, marginTop: 8 },
  desc: { color: t.colors.textSub, fontSize: 13, lineHeight: 19, marginTop: 8 },
  section: { fontSize: 17, fontWeight: '800', color: t.colors.text, marginTop: 30, marginBottom: 12 },
  input: { ...inputBase(t), marginTop: 14 },
  pick: { width: 74, padding: 3, borderWidth: 2, borderColor: 'transparent', borderRadius: 16 },
  pickOn: { borderColor: t.colors.primary },
  pickName: { fontSize: 11, marginTop: 4, color: t.colors.text, textAlign: 'center' },
  note: { color: t.colors.textMuted, fontSize: 12, marginTop: 14 },
}));
