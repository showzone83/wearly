import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { GarmentArt } from '@/components/GarmentArt';
import { Icon } from '@/components/Icon';
import { defaultName, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { Badge, Button, Header, Kicker, notify, Row, Screen, Serif } from '@/components/ui';
import { COLOR_KEYS, SUBTYPE, SUBTYPES_BY_CATEGORY } from '@/data/constants';
import type { Category, ColorKey, SubType } from '@/data/types';
import { newId } from '@/lib/context';
import { canRecommend } from '@/lib/recommend';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

type Step = 1 | 2 | 3 | 4;

const ANALYSIS_STEPS = ['종류 인식', '색상 추출', '패턴 · 계절 판단'];

function hash(str: string): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h;
}

/**
 * 목업 AI 분석: 실제 모델 대신 사진마다 고정된 제안값을 만든다.
 * 출시 버전에서는 POST /api/v1/wardrobe/items/analyze 응답으로 대체한다.
 */
function mockAnalyze(uri: string): ItemDraft {
  const h = hash(uri);
  const cats: Category[] = ['TOP', 'TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'];
  const category = cats[h % cats.length];
  const subs = SUBTYPES_BY_CATEGORY[category];
  const subType: SubType = subs[(h >> 3) % subs.length];
  const palette: ColorKey[] = COLOR_KEYS.slice(0, 8);
  const color = palette[(h >> 7) % palette.length];
  return {
    name: defaultName({ color, subType }),
    category,
    subType,
    color,
    pattern: 'SOLID',
    seasons: SUBTYPE[subType].warmth >= 2 ? ['FALL', 'WINTER'] : ['SPRING', 'FALL'],
    styles: ['MINIMAL'],
    formality: 2,
    status: 'OWNED',
    imageUri: uri,
  };
}

const MANUAL: ItemDraft = {
  name: '네이비 니트',
  category: 'TOP',
  subType: 'KNIT',
  color: 'navy',
  pattern: 'SOLID',
  seasons: ['FALL', 'WINTER'],
  styles: ['NEAT'],
  formality: 2,
  status: 'OWNED',
};

export default function NewItem() {
  const items = useStore((s) => s.items);
  const addItem = useStore((s) => s.addItem);
  const [step, setStep] = useState<Step>(1);
  const [draft, setDraft] = useState<ItemDraft | null>(null);
  const [fromAi, setFromAi] = useState(false);
  const [phase, setPhase] = useState(0);

  // 2단계: 분석 진행 표시 후 3단계로
  useEffect(() => {
    if (step !== 2) return;
    setPhase(0);
    const timers = ANALYSIS_STEPS.map((_, i) => setTimeout(() => setPhase(i + 1), 550 * (i + 1)));
    const done = setTimeout(() => setStep(3), 550 * ANALYSIS_STEPS.length + 300);
    return () => {
      timers.forEach(clearTimeout);
      clearTimeout(done);
    };
  }, [step]);

  const pick = async (source: 'camera' | 'library') => {
    try {
      if (source === 'camera' && Platform.OS !== 'web') {
        const perm = await ImagePicker.requestCameraPermissionsAsync();
        if (!perm.granted) {
          notify('카메라 권한이 필요해요', '설정에서 카메라 접근을 허용해 주세요.');
          return;
        }
      }
      const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.6, exif: false };
      const res = source === 'camera' ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
      if (res.canceled || !res.assets[0]) return;
      setDraft(mockAnalyze(res.assets[0].uri));
      setFromAi(true);
      setStep(2);
    } catch {
      notify('사진을 불러오지 못했어요', '직접 입력으로 등록할 수 있어요.');
    }
  };

  const save = () => {
    if (!draft) return;
    addItem({ ...draft, name: draft.name.trim() || defaultName(draft), id: newId('item'), wornCount: 0, createdAt: new Date().toISOString() });
    setStep(4);
  };

  const progress = (
    <View style={s.progress}>
      {[1, 2, 3, 4].map((n) => (
        <View key={n} style={[s.bar, { backgroundColor: n <= step ? colors.green : colors.line }]} />
      ))}
    </View>
  );

  if (step === 4) {
    const after = canRecommend(useStore.getState().items);
    return (
      <Screen
        header={<Header title="옷 등록" />}
        footer={
          <View style={{ gap: 10 }}>
            <Button title="계속 등록하기" variant="outline" onPress={() => { setDraft(null); setStep(1); }} />
            <Button title={after.ok ? '오늘 코디 보러 가기' : '옷장으로'} onPress={() => router.replace(after.ok ? '/home' : '/closet')} />
          </View>
        }
      >
        {progress}
        <View style={{ alignItems: 'center', paddingTop: 40 }}>
          {draft && <GarmentArt subType={draft.subType} color={draft.color} pattern={draft.pattern} size={140} />}
          <Kicker>STEP 4 / 4</Kicker>
          <Serif style={s.doneTitle}>옷장에 추가했어요</Serif>
          <Text style={s.doneSub}>
            {after.ok
              ? `총 ${items.length}벌 · 이제 이 옷이 들어간 코디도 추천해 드려요.`
              : `첫 코디까지 ${after.missing.join(', ')} 1벌씩 남았어요.`}
          </Text>
        </View>
      </Screen>
    );
  }

  if (step === 3 && draft) {
    return (
      <Screen header={<Header title="옷 등록" />} footer={<Button title="옷장에 저장" onPress={save} />}>
        {progress}
        <Kicker>STEP 3 / 4</Kicker>
        <Row style={{ gap: 16, marginTop: 12, marginBottom: 26 }}>
          <View style={s.preview}>
            {draft.imageUri ? (
              <Image source={{ uri: draft.imageUri }} style={StyleSheet.absoluteFill} contentFit="cover" />
            ) : (
              <GarmentArt subType={draft.subType} color={draft.color} pattern={draft.pattern} size={86} />
            )}
          </View>
          <View style={{ flex: 1, gap: 6 }}>
            {fromAi && <Badge tone="green">AI 제안 · 목업</Badge>}
            <Text style={s.formTitle}>{fromAi ? '분석이 끝났어요' : '직접 입력해 주세요'}</Text>
            <Text style={s.formSub}>{fromAi ? '틀린 항목은 바로 수정하세요' : '사진은 나중에 추가할 수 있어요'}</Text>
          </View>
        </Row>
        <ItemForm value={draft} onChange={setDraft} aiFields={fromAi ? ['category', 'color', 'pattern'] : []} />
      </Screen>
    );
  }

  if (step === 2) {
    return (
      <Screen header={<Header title="옷 등록" />} scroll={false}>
        {progress}
        <View style={s.center}>
          {draft?.imageUri && <Image source={{ uri: draft.imageUri }} style={s.analyzing} contentFit="cover" />}
          <ActivityIndicator color={colors.green} style={{ marginTop: 28 }} />
          <Serif style={s.doneTitle}>AI가 분석하고 있어요</Serif>
          <View style={{ gap: 8, marginTop: 16 }}>
            {ANALYSIS_STEPS.map((t, i) => (
              <Row key={t} style={{ gap: 8 }}>
                <Icon name="check" size={16} color={i < phase ? colors.green : colors.line} />
                <Text style={{ color: i < phase ? colors.ink : colors.faint }}>{t}</Text>
              </Row>
            ))}
          </View>
          <Pressable onPress={() => { setFromAi(false); setDraft(MANUAL); setStep(3); }} style={{ marginTop: 28 }}>
            <Text style={s.manual}>직접 입력할게요</Text>
          </Pressable>
        </View>
      </Screen>
    );
  }

  return (
    <Screen header={<Header title="옷 등록" />}>
      {progress}
      <Kicker>STEP 1 / 4</Kicker>
      <Serif style={s.title}>사진 한 장이면{'\n'}충분해요</Serif>
      <Text style={s.desc}>AI가 종류·색상·패턴을 제안하고, 사용자는 확인만 하면 돼요. 위치정보(EXIF)는 저장하지 않아요.</Text>
      <View style={{ gap: 12, marginTop: 28 }}>
        <Pressable style={s.option} onPress={() => pick('camera')}>
          <Icon name="camera" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>카메라로 촬영</Text>
            <Text style={s.optSub}>밝은 바닥에 옷을 펼쳐 찍으면 인식이 잘 돼요</Text>
          </View>
        </Pressable>
        <Pressable style={s.option} onPress={() => pick('library')}>
          <Icon name="image" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>앨범에서 선택</Text>
            <Text style={s.optSub}>쇼핑몰 캡처 사진도 괜찮아요</Text>
          </View>
        </Pressable>
        <Pressable style={[s.option, { borderStyle: 'dashed' }]} onPress={() => { setFromAi(false); setDraft(MANUAL); setStep(3); }}>
          <Icon name="plus" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>사진 없이 직접 입력</Text>
            <Text style={s.optSub}>분석이 어렵거나 사진이 없을 때</Text>
          </View>
        </Pressable>
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  progress: { flexDirection: 'row', gap: 6, marginBottom: 18 },
  bar: { flex: 1, height: 3 },
  title: { fontSize: 27, lineHeight: 38, marginTop: 10 },
  desc: { color: colors.sub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  option: { flexDirection: 'row', alignItems: 'center', gap: 16, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 18 },
  optTitle: { fontSize: 16, fontWeight: '800', color: colors.ink },
  optSub: { fontSize: 12, color: colors.sub, marginTop: 3 },
  center: { flex: 1, alignItems: 'center', paddingTop: 30 },
  analyzing: { width: 180, height: 180, backgroundColor: colors.tile },
  doneTitle: { fontSize: 22, marginTop: 14, textAlign: 'center' },
  doneSub: { color: colors.sub, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  manual: { color: colors.green, fontWeight: '700', textDecorationLine: 'underline' },
  preview: { width: 110, height: 110, backgroundColor: colors.tile, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  formTitle: { fontSize: 18, fontWeight: '800', color: colors.ink },
  formSub: { fontSize: 13, color: colors.sub },
});
